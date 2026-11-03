package api_bartz.info_nf.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import api_bartz.info_nf.dto.PedidoDTO;

@Service 
public class PedidoService {

    private static final Logger log = LoggerFactory.getLogger(PedidoService.class);

    private final JdbcTemplate db2JdbcTemplate;
    private final JdbcTemplate mysqlJdbcTemplate;

    public PedidoService(
            @Qualifier("db2JdbcTemplate") JdbcTemplate db2JdbcTemplate, 
            @Qualifier("mysqlJdbcTemplate") JdbcTemplate mysqlJdbcTemplate) {
        this.db2JdbcTemplate = db2JdbcTemplate;
        this.mysqlJdbcTemplate = mysqlJdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<PedidoDTO> listarTodosPedidos() {
        String sql = "SELECT num_pedido, txt_cliente_nome FROM tab_pedido";

        return mysqlJdbcTemplate.query(sql, (rs, rowNum) -> {
            PedidoDTO pedido = new PedidoDTO();
            String numeroPedido = rs.getString("num_pedido").trim();
            String nomeCliente = rs.getString("txt_cliente_nome").trim();
            pedido.setNumeroPedido(numeroPedido);
            pedido.setNomeCliente(nomeCliente);
            return pedido;
        });
    }

    @Transactional(readOnly = true)
    public List<PedidoDTO> procurarPedido(String pedido) {
        String sql = "SELECT num_pedido, txt_cliente_nome FROM tab_pedido WHERE UPPER(num_pedido) LIKE UPPER(?)";
        String sqlCorrigido = "%" + pedido + "%";

        return mysqlJdbcTemplate.query(sql, (rs, rowNum) -> {
            PedidoDTO p = new PedidoDTO();
            String numeroPedido = rs.getString("num_pedido").trim();
            String nomeCliente = rs.getString("txt_cliente_nome").trim();
            p.setNumeroPedido(numeroPedido);
            p.setNomeCliente(nomeCliente);
            return p;
        }, sqlCorrigido);
    }

    // Record auxiliar para armazenar o número do pedido e o valor atual de observacao_5
    private record PedidoPendenteDb2(String nroPedido, String observacaoAtual) {}

    /**
     * TAREFA AGENDADA (Worker em Segundo Plano):
     * fixedDelay = 30000 -> roda a cada 30 segundos após o término da rodada anterior.
     * 
     * 1. Consulta no DB2 os pedidos na tabela 'pedido' onde 'observacao_5' está vazia/nula OU não contém 'Nome Cliente:'.
     * 2. Para cada um, busca no MySQL (tabela 'tab_pedido') o campo 'txt_cliente_nome'.
     * 3. Se a observação já tiver outro valor prévio, preserva e anexa "Nome Cliente: [nome]".
     *    Caso contrário, grava diretamente "Nome Cliente: [nome]".
     */
    @Scheduled(fixedDelay = 30000)
    public void rotinaSincronizarNovosPedidos() {
        // 1. Busca no DB2 pedidos onde observacao_5 é nula, vazia OU ainda NÃO contém 'Nome Cliente:'
        String sqlDb2BuscaPendentes = 
            "SELECT nro_pedido, observacao_5 FROM pedido WHERE observacao_5 IS NULL OR UPPER(observacao_5) NOT LIKE '%NOME CLIENTE:%'";

        List<PedidoPendenteDb2> pedidosPendentes = db2JdbcTemplate.query(
            sqlDb2BuscaPendentes, 
            (rs, rowNum) -> new PedidoPendenteDb2(
                rs.getString("nro_pedido") != null ? rs.getString("nro_pedido").trim() : "",
                rs.getString("observacao_5")
            )
        );

        if (pedidosPendentes.isEmpty()) {
            return; // Nenhum pedido novo pendente no DB2, aguarda a próxima checagem
        }

        log.info("🔍 [SINCRONIZADOR] Encontrados {} pedidos no DB2 precisando verificar/adicionar 'Nome Cliente:'.", pedidosPendentes.size());

        // 2. Queries exatas com as tabelas e campos reais:
        String sqlMysqlBuscaCliente = "SELECT txt_cliente_nome FROM tab_pedido WHERE TRIM(num_pedido) = ? LIMIT 1";
        String sqlDb2Update = "UPDATE pedido SET observacao_5 = ? WHERE nro_pedido = ?";

        for (PedidoPendenteDb2 item : pedidosPendentes) {
            String nroPedidoOriginal = item.nroPedido();
            String observacaoAtual = item.observacaoAtual();

            // Garantia extra: se por ventura já possuir 'Nome Cliente:', não altera
            if (observacaoAtual != null && observacaoAtual.toUpperCase().contains("NOME CLIENTE:")) {
                continue;
            }

            try {
                // REGRA: Se o pedido do ERP tiver 6 dígitos e começar com '5',
                // removemos o primeiro '5' para buscar os 5 dígitos no MySQL (ex: 512345 -> 12345)
                String nroPedidoBusca = nroPedidoOriginal;
                if (nroPedidoBusca.length() == 6 && nroPedidoBusca.startsWith("5")) {
                    nroPedidoBusca = nroPedidoBusca.substring(1);
                }

                // 3. Busca o nome do cliente no MySQL usando o número ajustado de 5 dígitos
                String nomeClienteMysql = mysqlJdbcTemplate.queryForObject(
                    sqlMysqlBuscaCliente, 
                    String.class, 
                    nroPedidoBusca
                );

                if (nomeClienteMysql != null && !nomeClienteMysql.isBlank()) {
                    String textoCliente = "Nome Cliente: " + nomeClienteMysql.trim();
                    String novaObservacao;

                    // Se já existia um texto prévio, preserva e anexa o Nome Cliente
                    if (observacaoAtual != null && !observacaoAtual.isBlank()) {
                        novaObservacao = observacaoAtual.trim() + " - " + textoCliente;
                    } else {
                        novaObservacao = textoCliente;
                    }

                    // 4. Dá o UPDATE no DB2 usando a chave original do ERP (nroPedidoOriginal)
                    db2JdbcTemplate.update(sqlDb2Update, novaObservacao, nroPedidoOriginal);
                    log.info("✅ Pedido {} (busca MySQL: {}) atualizado no DB2! observacao_5 gravada como: '{}'", 
                            nroPedidoOriginal, nroPedidoBusca, novaObservacao);
                }

            } 
            catch (EmptyResultDataAccessException e) {
                // O pedido já entrou no DB2, mas ainda não existe no MySQL (pode estar sendo digitado/transmitido)
                log.warn("⏳ Pedido {} está no DB2, mas ainda não consta na tab_pedido do MySQL.", nroPedidoOriginal);
            } 
            catch (Exception e) {
                log.error("❌ Erro ao atualizar observacao_5 do pedido {} no DB2: {}", nroPedidoOriginal, e.getMessage());
            }
        }
    }
}
