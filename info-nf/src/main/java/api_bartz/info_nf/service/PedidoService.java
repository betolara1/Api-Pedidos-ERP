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
import api_bartz.info_nf.dto.PedidoPendenteDb2;

@Service 
public class PedidoService {

    private static final Logger log = LoggerFactory.getLogger(PedidoService.class);

    private final JdbcTemplate db2JdbcTemplate; // Banco de dados do DB2
    private final JdbcTemplate mysqlJdbcTemplate; // Banco de dados do MySQL

    // Record auxiliar interno para transportar dados do cliente lidos do MySQL
    private record DadosClienteMysql(
        String nome,
        String endereco,
        String bairro,
        String cidade,
        String cep
    ) {}

    public PedidoService(
            @Qualifier("db2JdbcTemplate") JdbcTemplate db2JdbcTemplate, 
            @Qualifier("mysqlJdbcTemplate") JdbcTemplate mysqlJdbcTemplate) {
        this.db2JdbcTemplate = db2JdbcTemplate;
        this.mysqlJdbcTemplate = mysqlJdbcTemplate;
    }

    /**
     * Consulta que retorna todos os pedidos
     */
    @Transactional(readOnly = true)
    public List<PedidoDTO> listarTodosPedidos() {
        String sql = "SELECT num_pedido, txt_cliente_nome, txt_cliente_endereco, txt_cliente_bairro, txt_cliente_cidade, txt_cliente_cep FROM tab_pedido"; 

        return mysqlJdbcTemplate.query(sql, (rs, rowNum) -> {
            PedidoDTO pedido = new PedidoDTO(); 

            String numeroPedido = rs.getString("num_pedido") != null ? rs.getString("num_pedido").trim() : "";
            String nomeCliente = rs.getString("txt_cliente_nome") != null ? rs.getString("txt_cliente_nome").trim() : "";
            String enderecoCliente = rs.getString("txt_cliente_endereco") != null ? rs.getString("txt_cliente_endereco").trim() : "";
            String bairroCliente = rs.getString("txt_cliente_bairro") != null ? rs.getString("txt_cliente_bairro").trim() : "";
            String cidadeCliente = rs.getString("txt_cliente_cidade") != null ? rs.getString("txt_cliente_cidade").trim() : "";
            String cepCliente = rs.getString("txt_cliente_cep") != null ? rs.getString("txt_cliente_cep").trim() : "";

            pedido.setNumeroPedido(numeroPedido);
            pedido.setNomeCliente(nomeCliente);
            pedido.setEndereco(enderecoCliente);
            pedido.setBairro(bairroCliente);
            pedido.setCidade(cidadeCliente);
            pedido.setCep(cepCliente);

            return pedido;
        });
    }

    /**
     * Consulta que retorna um pedido em especifico
     */
    @Transactional(readOnly = true)
    public List<PedidoDTO> procurarPedido(String pedido) {
        String sql = "SELECT num_pedido, txt_cliente_nome, txt_cliente_endereco, txt_cliente_bairro, txt_cliente_cidade, txt_cliente_cep FROM tab_pedido WHERE UPPER(num_pedido) LIKE UPPER(?)";
        String sqlCorrigido = "%" + pedido + "%";

        return mysqlJdbcTemplate.query(sql, (rs, rowNum) -> {
            PedidoDTO p = new PedidoDTO();

            String numeroPedido = rs.getString("num_pedido") != null ? rs.getString("num_pedido").trim() : "";
            String nomeCliente = rs.getString("txt_cliente_nome") != null ? rs.getString("txt_cliente_nome").trim() : "";
            String enderecoCliente = rs.getString("txt_cliente_endereco") != null ? rs.getString("txt_cliente_endereco").trim() : "";
            String bairroCliente = rs.getString("txt_cliente_bairro") != null ? rs.getString("txt_cliente_bairro").trim() : "";
            String cidadeCliente = rs.getString("txt_cliente_cidade") != null ? rs.getString("txt_cliente_cidade").trim() : "";
            String cepCliente = rs.getString("txt_cliente_cep") != null ? rs.getString("txt_cliente_cep").trim() : "";

            p.setNumeroPedido(numeroPedido);
            p.setNomeCliente(nomeCliente);
            p.setEndereco(enderecoCliente);
            p.setBairro(bairroCliente);
            p.setCidade(cidadeCliente);
            p.setCep(cepCliente);

            return p;
        }, sqlCorrigido);
    }

    /**
     * TAREFA AGENDADA (Worker em Segundo Plano):
     * fixedDelay = 30000 -> roda a cada 30 segundos após o término da rodada anterior.
     * 
     * 1. Consulta no DB2 os pedidos na tabela 'pedido' onde 'observacao_5' está vazia/nula OU não contém 'Nome Cliente:'.
     * 2. Para cada um, busca no MySQL (tabela 'tab_pedido') os dados do cliente (nome e endereço).
     * 3. Grava no DB2:
     *    - 'observacao_5': "Nome Cliente: [nome]" (obrigatório, preservando conteúdo prévio se houver)
     *    - 'observacao_4': "Endereço: [endereço completo]" (opcional, apenas se houver endereço preenchido, preservando conteúdo prévio)
     */
    @Scheduled(fixedDelay = 30000)
    public void rotinaSincronizarNovosPedidos() {
        List<PedidoPendenteDb2> pedidosPendentes = buscarPedidosPendentesDb2();

        if (pedidosPendentes.isEmpty()) {
            return; // Nenhum pedido novo pendente no DB2, aguarda a próxima checagem
        }

        //log.info("🔍 [SINCRONIZADOR] Encontrados {} pedidos no DB2 precisando verificar/adicionar 'Nome Cliente:'.", pedidosPendentes.size());

        // Queries com as tabelas e campos:
        String sqlMysqlBuscaCliente = "SELECT txt_cliente_nome, txt_cliente_endereco, txt_cliente_bairro, txt_cliente_cidade, txt_cliente_cep FROM tab_pedido WHERE TRIM(num_pedido) = ? LIMIT 1";
        String sqlDb2UpdateComEndereco = "UPDATE pedido SET observacao_5 = ?, observacao_4 = ? WHERE nro_pedido = ?";
        String sqlDb2UpdateSemEndereco = "UPDATE pedido SET observacao_5 = ? WHERE nro_pedido = ?";

        for (PedidoPendenteDb2 item : pedidosPendentes) {
            String nroPedidoOriginal = item.nroPedido();
            String observacao5Atual = item.observacao5();
            String observacao4Atual = item.observacao4();

            // Garantia extra: se por ventura já possuir 'Nome Cliente:', não altera
            if (observacao5Atual != null && observacao5Atual.toUpperCase().contains("NOME CLIENTE:")) {
                continue;
            }

            try {
                // Ajusta número (remove o '5' inicial caso possua 6 dígitos)
                String nroPedidoBusca = ajustarNumeroPedidoParaBusca(nroPedidoOriginal);

                // 3. Busca os dados do cliente no MySQL
                DadosClienteMysql clienteMysql = mysqlJdbcTemplate.queryForObject(
                    sqlMysqlBuscaCliente, 
                    (rs, rowNum) -> new DadosClienteMysql(
                        rs.getString("txt_cliente_nome") != null ? rs.getString("txt_cliente_nome").trim() : null,
                        rs.getString("txt_cliente_endereco") != null ? rs.getString("txt_cliente_endereco").trim() : null,
                        rs.getString("txt_cliente_bairro") != null ? rs.getString("txt_cliente_bairro").trim() : null,
                        rs.getString("txt_cliente_cidade") != null ? rs.getString("txt_cliente_cidade").trim() : null,
                        rs.getString("txt_cliente_cep") != null ? rs.getString("txt_cliente_cep").trim() : null
                    ),
                    nroPedidoBusca
                );

                if (clienteMysql != null && clienteMysql.nome() != null && !clienteMysql.nome().isBlank()) {
                    // Prepara observacao_5 (Nome do cliente - Obrigatório)
                    String textoCliente = "Nome Cliente: " + clienteMysql.nome();
                    String novaObservacao5;

                    if (observacao5Atual != null && !observacao5Atual.isBlank()) {
                        novaObservacao5 = observacao5Atual.trim() + " - " + textoCliente;
                    } else {
                        novaObservacao5 = textoCliente;
                    }

                    // Prepara observacao_4 (Endereço completo - Opcional)
                    String enderecoFormatado = montarEnderecoCompleto(
                        clienteMysql.endereco(),
                        clienteMysql.bairro(),
                        clienteMysql.cidade(),
                        clienteMysql.cep()
                    );

                    if (enderecoFormatado != null && !enderecoFormatado.isBlank()) {
                        String textoEndereco = "Endereço: " + enderecoFormatado;
                        String novaObservacao4;

                        if (observacao4Atual != null && !observacao4Atual.isBlank()) {
                            if (!observacao4Atual.toUpperCase().contains("ENDEREÇO:") && !observacao4Atual.toUpperCase().contains("ENDERECO:")) {
                                novaObservacao4 = observacao4Atual.trim() + " - " + textoEndereco;
                            } else {
                                novaObservacao4 = observacao4Atual;
                            }
                        } else {
                            novaObservacao4 = textoEndereco;
                        }

                        // Atualiza DB2 com observacao_5 e observacao_4
                        db2JdbcTemplate.update(sqlDb2UpdateComEndereco, novaObservacao5, novaObservacao4, nroPedidoOriginal);
                        log.info("✅ Pedido {} (busca MySQL: {}) atualizado no DB2! observacao_5: '{}' | observacao_4: '{}'", 
                                nroPedidoOriginal, nroPedidoBusca, novaObservacao5, novaObservacao4);
                    } else {
                        // Atualiza DB2 apenas com observacao_5 (sem endereço)
                        db2JdbcTemplate.update(sqlDb2UpdateSemEndereco, novaObservacao5, nroPedidoOriginal);
                        log.info("✅ Pedido {} (busca MySQL: {}) atualizado no DB2! observacao_5: '{}' (Sem endereço)", 
                                nroPedidoOriginal, nroPedidoBusca, novaObservacao5);
                    }
                }

            } 
            catch (EmptyResultDataAccessException e) {
                // O pedido já entrou no DB2, mas ainda não existe no MySQL (pode estar sendo digitado/transmitido)
                //log.debug("⏳ Pedido {} está no DB2, mas ainda não consta na tab_pedido do MySQL.", nroPedidoOriginal);
            } 
            catch (Exception e) {
                log.error("❌ Erro ao atualizar observações do pedido {} no DB2: {}", nroPedidoOriginal, e.getMessage(), e);
            }
        }
    }

    /**
     * Busca no DB2 pedidos onde observacao_5 é nula, vazia OU ainda NÃO contém 'Nome Cliente:'
     */
    private List<PedidoPendenteDb2> buscarPedidosPendentesDb2() {
        String sqlDb2BuscaPendentes = "SELECT nro_pedido, observacao_4, observacao_5 FROM pedido WHERE observacao_5 IS NULL OR UPPER(observacao_5) NOT LIKE '%NOME CLIENTE:%'";

        return db2JdbcTemplate.query(
            sqlDb2BuscaPendentes, 
            (rs, rowNum) -> new PedidoPendenteDb2(
                rs.getString("nro_pedido") != null ? rs.getString("nro_pedido").trim() : "",
                rs.getString("observacao_4"),
                rs.getString("observacao_5")
            )
        );
    }

    /**
     * Concatena os campos de endereço disponíveis no formato:
     * "Endereço, Bairro - Cidade / CEP: 12345-678"
     */
    private String montarEnderecoCompleto(String endereco, String bairro, String cidade, String cep) {
        StringBuilder sb = new StringBuilder();

        if (endereco != null && !endereco.isBlank()) {
            sb.append(endereco.trim());
        }

        if (bairro != null && !bairro.isBlank()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(bairro.trim());
        }

        if (cidade != null && !cidade.isBlank()) {
            if (!sb.isEmpty()) sb.append(" - ");
            sb.append(cidade.trim());
        }

        if (cep != null && !cep.isBlank()) {
            if (!sb.isEmpty()) sb.append(" / CEP: ");
            sb.append(cep.trim());
        }

        String resultado = sb.toString().trim();
        return resultado.isEmpty() ? null : resultado;
    }

    /**
     * REGRA: Se o pedido do ERP tiver 6 dígitos e começar com '5',
     * removemos o primeiro '5' para buscar os 5 dígitos no MySQL (ex: 512345 -> 12345).
     */
    private String ajustarNumeroPedidoParaBusca(String nroPedidoOriginal) {
        if (nroPedidoOriginal != null && nroPedidoOriginal.length() == 6 && nroPedidoOriginal.startsWith("5")) {
            return nroPedidoOriginal.substring(1);
        }
        return nroPedidoOriginal;
    }
}
