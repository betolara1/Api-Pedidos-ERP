package api_bartz.info_nf.dto;

public record PedidoPendenteDb2(String nroPedido, String observacao4, String observacao5) {
    public PedidoPendenteDb2(String nroPedido, String observacao5) {
        this(nroPedido, null, observacao5);
    }

    public String observacaoAtual() {
        return observacao5;
    }
}
