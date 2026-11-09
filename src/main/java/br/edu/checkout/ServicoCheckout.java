package br.edu.checkout;

import java.math.BigDecimal;
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
        validar(solicitacao);
        BigDecimal total = calcularTotal(solicitacao);
        if (!estoqueGateway.reservar(solicitacao.sku(), solicitacao.quantidade())) {
            throw new EstoqueInsuficienteException();
        }
        Pagamento pagamento = cobrarOuLiberarReserva(solicitacao, total);
        String numeroPedido = pedidoRepository.salvar(new PedidoConfirmado(
                solicitacao.clienteId(),
                solicitacao.email(),
                solicitacao.sku(),
                solicitacao.quantidade(),
                total,
                pagamento.transacaoId()));
        boolean notificacaoEnviada;
        try {
            notificador.enviarConfirmacao(solicitacao.email(), numeroPedido, total);
            notificacaoEnviada = true;
        } catch (FalhaNotificacaoException falha) {
            notificacaoEnviada = false;
        }
        return new ResultadoCheckout(numeroPedido, total, notificacaoEnviada);
    }

    private Pagamento cobrarOuLiberarReserva(SolicitacaoCheckout solicitacao, BigDecimal total) {
        Pagamento pagamento;
        try {
            pagamento = pagamentoGateway.cobrar(solicitacao.clienteId(), total);
        } catch (FalhaPagamentoException falhaTecnica) {
            liberarReserva(solicitacao);
            throw falhaTecnica;
        }
        if (!pagamento.aprovado()) {
            liberarReserva(solicitacao);
            throw new PagamentoRecusadoException();
        }
        return pagamento;
    }

    private void liberarReserva(SolicitacaoCheckout solicitacao) {
        estoqueGateway.liberar(solicitacao.sku(), solicitacao.quantidade());
    }

    private static BigDecimal calcularTotal(SolicitacaoCheckout solicitacao) {
        return solicitacao.precoUnitario()
                .multiply(BigDecimal.valueOf(solicitacao.quantidade()));
    }

    private static void validar(SolicitacaoCheckout solicitacao) {
        if (solicitacao == null) {
            throw new IllegalArgumentException("solicitacao nao pode ser nula");
        }
        exigirTexto(solicitacao.clienteId(), "clienteId");
        exigirTexto(solicitacao.email(), "email");
        exigirTexto(solicitacao.sku(), "sku");
        if (solicitacao.quantidade() <= 0) {
            throw new IllegalArgumentException("quantidade deve ser positiva");
        }
        if (solicitacao.precoUnitario() == null
                || solicitacao.precoUnitario().signum() <= 0) {
            throw new IllegalArgumentException("precoUnitario deve ser positivo");
        }
    }

    private static void exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " obrigatorio");
        }
    }
}
