package api_bartz.info_nf.dto;

import lombok.Data;

@Data 
public class PedidoDTO {
    private String numeroPedido;
    private String nomeCliente;
    private String endereco;
    private String bairro;
    private String cidade;
    private String cep;

    public PedidoDTO(){}

    public PedidoDTO(String numeroPedido, String nomeCliente, String endereco, String bairro, String cidade, String cep){
        this.numeroPedido = numeroPedido;
        this.nomeCliente = nomeCliente;
        this.endereco = endereco;
        this.bairro = bairro;
        this.cidade = cidade;
        this.cep = cep;
    }
}
