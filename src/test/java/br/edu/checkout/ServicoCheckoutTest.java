package br.edu.checkout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class ServicoCheckoutTest {

    @Mock
    private EstoqueGateway estoqueGateway;

    @Mock
    private PagamentoGateway pagamentoGateway;

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private Notificador notificador;

    @Captor
    private ArgumentCaptor<PedidoConfirmado> pedidoCaptor;

    private ServicoCheckout servico;

    @BeforeEach
    void criarServicoReal() {
        servico = new ServicoCheckout(
                estoqueGateway, pagamentoGateway, pedidoRepository, notificador);
    }

    @Test
    void deveRejeitarSolicitacaoNulaSemConsultarColaboradores() {
        assertThrows(IllegalArgumentException.class, () -> servico.finalizar(null));
        verifyNoInteractions(
                estoqueGateway, pagamentoGateway, pedidoRepository, notificador);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("solicitacoesInvalidas")
    void deveRejeitarSolicitacaoInvalidaSemConsultarColaboradores(
            String descricao, SolicitacaoCheckout invalida) {
        assertThrows(IllegalArgumentException.class, () -> servico.finalizar(invalida));
        verifyNoInteractions(
                estoqueGateway, pagamentoGateway, pedidoRepository, notificador);
    }

    @Test
    void deveAceitarUmaUnidadeMantendoPrecoComoTotal() {
        BigDecimal preco = new BigDecimal("19.90");
        when(estoqueGateway.reservar("SKU-1", 1)).thenReturn(true);
        when(pagamentoGateway.cobrar("cliente-1", preco))
                .thenReturn(new Pagamento(true, "TX-1"));
        when(pedidoRepository.salvar(any(PedidoConfirmado.class))).thenReturn("PED-1");

        ResultadoCheckout resultado = servico.finalizar(solicitacao(1, "19.90"));

        assertEquals(preco, resultado.total());
    }

    @Test
    void deveCalcularTotalSemArredondarPrecoComTresCasas() {
        BigDecimal total = new BigDecimal("0.015");
        when(estoqueGateway.reservar("SKU-1", 3)).thenReturn(true);
        when(pagamentoGateway.cobrar("cliente-1", total))
                .thenReturn(new Pagamento(true, "TX-1"));
        when(pedidoRepository.salvar(any(PedidoConfirmado.class))).thenReturn("PED-1");

        ResultadoCheckout resultado = servico.finalizar(solicitacao(3, "0.005"));

        // equals de BigDecimal compara escala: 0.02 ou 0.0150 reprovariam aqui.
        assertEquals(total, resultado.total());
        verify(pagamentoGateway).cobrar("cliente-1", total);
        verify(notificador).enviarConfirmacao("ana@exemplo.com", "PED-1", total);
    }

    @Test
    void devePersistirPedidoComOsSeisCamposCorretos() {
        BigDecimal total = new BigDecimal("59.70");
        when(estoqueGateway.reservar("SKU-1", 3)).thenReturn(true);
        when(pagamentoGateway.cobrar("cliente-1", total))
                .thenReturn(new Pagamento(true, "TX-777"));
        when(pedidoRepository.salvar(any(PedidoConfirmado.class))).thenReturn("PED-1");

        servico.finalizar(solicitacao(3, "19.90"));

        verify(pedidoRepository).salvar(pedidoCaptor.capture());
        PedidoConfirmado salvo = pedidoCaptor.getValue();
        assertEquals("cliente-1", salvo.clienteId());
        assertEquals("ana@exemplo.com", salvo.email());
        assertEquals("SKU-1", salvo.sku());
        assertEquals(3, salvo.quantidade());
        assertEquals(total, salvo.total());
        assertEquals("TX-777", salvo.transacaoId());
    }

    @Test
    void deveReservarCobrarSalvarENotificarNessaOrdemSemLiberarEstoque() {
        BigDecimal total = new BigDecimal("59.70");
        when(estoqueGateway.reservar("SKU-1", 3)).thenReturn(true);
        when(pagamentoGateway.cobrar("cliente-1", total))
                .thenReturn(new Pagamento(true, "TX-1"));
        when(pedidoRepository.salvar(any(PedidoConfirmado.class))).thenReturn("PED-1");

        servico.finalizar(solicitacao(3, "19.90"));

        InOrder ordem = inOrder(estoqueGateway, pagamentoGateway, pedidoRepository, notificador);
        ordem.verify(estoqueGateway, times(1)).reservar("SKU-1", 3);
        ordem.verify(pagamentoGateway, times(1)).cobrar("cliente-1", total);
        ordem.verify(pedidoRepository, times(1)).salvar(any(PedidoConfirmado.class));
        ordem.verify(notificador, times(1)).enviarConfirmacao("ana@exemplo.com", "PED-1", total);
        verify(estoqueGateway, never()).liberar(anyString(), anyInt());
    }

    @Test
    void deveRecusarCompraSemEstoqueSemCobrarRegistrarNotificarNemLiberar() {
        when(estoqueGateway.reservar("SKU-1", 3)).thenReturn(false);

        assertThrows(EstoqueInsuficienteException.class,
                () -> servico.finalizar(solicitacao(3, "19.90")));

        verify(estoqueGateway, times(1)).reservar("SKU-1", 3);
        verify(estoqueGateway, never()).liberar(anyString(), anyInt());
        verifyNoInteractions(pagamentoGateway, pedidoRepository, notificador);
    }

    @Test
    void deveLiberarReservaUmaVezQuandoPagamentoForRecusado() {
        BigDecimal total = new BigDecimal("59.70");
        when(estoqueGateway.reservar("SKU-1", 3)).thenReturn(true);
        when(pagamentoGateway.cobrar("cliente-1", total))
                .thenReturn(new Pagamento(false, null));

        assertThrows(PagamentoRecusadoException.class,
                () -> servico.finalizar(solicitacao(3, "19.90")));

        InOrder ordem = inOrder(estoqueGateway, pagamentoGateway);
        ordem.verify(estoqueGateway, times(1)).reservar("SKU-1", 3);
        ordem.verify(pagamentoGateway, times(1)).cobrar("cliente-1", total);
        ordem.verify(estoqueGateway, times(1)).liberar("SKU-1", 3);
        verifyNoInteractions(pedidoRepository, notificador);
    }

    @Test
    void deveLiberarReservaEPropagarAMesmaFalhaTecnicaDePagamento() {
        BigDecimal total = new BigDecimal("59.70");
        FalhaPagamentoException falha = new FalhaPagamentoException("gateway indisponivel");
        when(estoqueGateway.reservar("SKU-1", 3)).thenReturn(true);
        when(pagamentoGateway.cobrar("cliente-1", total)).thenThrow(falha);

        FalhaPagamentoException propagada = assertThrows(FalhaPagamentoException.class,
                () -> servico.finalizar(solicitacao(3, "19.90")));

        assertSame(falha, propagada);
        InOrder ordem = inOrder(estoqueGateway, pagamentoGateway);
        ordem.verify(estoqueGateway, times(1)).reservar("SKU-1", 3);
        ordem.verify(pagamentoGateway, times(1)).cobrar("cliente-1", total);
        ordem.verify(estoqueGateway, times(1)).liberar("SKU-1", 3);
        verifyNoInteractions(pedidoRepository, notificador);
    }

    private static SolicitacaoCheckout solicitacao(int quantidade, String precoUnitario) {
        return new SolicitacaoCheckout(
                "cliente-1", "ana@exemplo.com", "SKU-1", quantidade, new BigDecimal(precoUnitario));
    }

    static Stream<Arguments> solicitacoesInvalidas() {
        BigDecimal preco = new BigDecimal("19.90");
        return Stream.of(
                Arguments.of("clienteId nulo",
                        new SolicitacaoCheckout(null, "ana@exemplo.com", "SKU-1", 1, preco)),
                Arguments.of("clienteId vazio",
                        new SolicitacaoCheckout("", "ana@exemplo.com", "SKU-1", 1, preco)),
                Arguments.of("clienteId em branco",
                        new SolicitacaoCheckout("   ", "ana@exemplo.com", "SKU-1", 1, preco)),
                Arguments.of("email nulo",
                        new SolicitacaoCheckout("cliente-1", null, "SKU-1", 1, preco)),
                Arguments.of("email vazio",
                        new SolicitacaoCheckout("cliente-1", "", "SKU-1", 1, preco)),
                Arguments.of("email em branco",
                        new SolicitacaoCheckout("cliente-1", "   ", "SKU-1", 1, preco)),
                Arguments.of("sku nulo",
                        new SolicitacaoCheckout("cliente-1", "ana@exemplo.com", null, 1, preco)),
                Arguments.of("sku vazio",
                        new SolicitacaoCheckout("cliente-1", "ana@exemplo.com", "", 1, preco)),
                Arguments.of("sku em branco",
                        new SolicitacaoCheckout("cliente-1", "ana@exemplo.com", "   ", 1, preco)),
                Arguments.of("quantidade zero",
                        new SolicitacaoCheckout("cliente-1", "ana@exemplo.com", "SKU-1", 0, preco)),
                Arguments.of("quantidade negativa",
                        new SolicitacaoCheckout("cliente-1", "ana@exemplo.com", "SKU-1", -1, preco)),
                Arguments.of("preco nulo",
                        new SolicitacaoCheckout("cliente-1", "ana@exemplo.com", "SKU-1", 1, null)),
                Arguments.of("preco zero",
                        new SolicitacaoCheckout(
                                "cliente-1", "ana@exemplo.com", "SKU-1", 1, BigDecimal.ZERO)),
                Arguments.of("preco negativo",
                        new SolicitacaoCheckout(
                                "cliente-1", "ana@exemplo.com", "SKU-1", 1, new BigDecimal("-0.01"))));
    }

    @Test
    void deveConfirmarCompraAprovadaComTotalDeTresUnidades() {
        SolicitacaoCheckout solicitacao = solicitacao(3, "19.90");
        BigDecimal total = new BigDecimal("59.70");
        when(estoqueGateway.reservar("SKU-1", 3)).thenReturn(true);
        when(pagamentoGateway.cobrar("cliente-1", total))
                .thenReturn(new Pagamento(true, "TX-1"));
        when(pedidoRepository.salvar(any(PedidoConfirmado.class))).thenReturn("PED-1");

        ResultadoCheckout resultado = servico.finalizar(solicitacao);

        assertEquals(new ResultadoCheckout("PED-1", total, true), resultado);
        verify(notificador).enviarConfirmacao("ana@exemplo.com", "PED-1", total);
    }
}
