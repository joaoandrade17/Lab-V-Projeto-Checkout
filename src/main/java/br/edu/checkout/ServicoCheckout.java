package br.edu.checkout;

import java.util.Objects;

public final class ServicoCheckout {

    private final EstoqueGateway estoqueGateway;
    private final PagamentoGateway pagamentoGateway;
    private final PedidoRepository pedidoRepository;
    private final Notificador notificador;

    public ServicoCheckout(
            EstoqueGateway estoqueGateway,
            PagamentoGateway pagamentoGateway,
            PedidoRepository pedidoRepository,
            Notificador notificador) {
        this.estoqueGateway = Objects.requireNonNull(estoqueGateway);
        this.pagamentoGateway = Objects.requireNonNull(pagamentoGateway);
        this.pedidoRepository = Objects.requireNonNull(pedidoRepository);
        this.notificador = Objects.requireNonNull(notificador);
    }

    public ResultadoCheckout finalizar(SolicitacaoCheckout solicitacao) {
        if (solicitacao == null) {
            throw new IllegalArgumentException("solicitacao nao pode ser nula");
        }
        if (solicitacao.clienteId() == null || solicitacao.clienteId().isBlank()) {
            throw new IllegalArgumentException("clienteId obrigatorio");
        }
        if (solicitacao.email() == null || solicitacao.email().isBlank()) {
            throw new IllegalArgumentException("email obrigatorio");
        }
        if (solicitacao.sku() == null || solicitacao.sku().isBlank()) {
            throw new IllegalArgumentException("sku obrigatorio");
        }
        if (solicitacao.quantidade() <= 0) {
            throw new IllegalArgumentException("quantidade deve ser positiva");
        }
        if (solicitacao.precoUnitario() == null
                || solicitacao.precoUnitario().signum() <= 0) {
            throw new IllegalArgumentException("precoUnitario deve ser positivo");
        }
        throw new UnsupportedOperationException("Implemente por TDD");
    }
}
