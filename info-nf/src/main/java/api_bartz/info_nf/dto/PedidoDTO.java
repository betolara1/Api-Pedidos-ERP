package api_bartz.info_nf.dto;

import lombok.Data;

@Data 
public class PedidoDTO {
    private String numeroPedido;
    private String nomeCliente;

    public PedidoDTO(){}

    public PedidoDTO(String numeroPedido, String nomeCliente){
        this.numeroPedido = numeroPedido;
        this.nomeCliente = nomeCliente;
    }
}
