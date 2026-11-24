-- =====================================================================
-- Agiliza Delivery - indices das consultas mais usadas (T014, RNF03)
--
-- A V1 indexa as FKs de navegacao. Faltavam as consultas que filtram por
-- periodo ou estado e que crescem com o historico da loja.
-- =====================================================================

-- Extrato, acerto do app e recibo: entregas da loja finalizadas num periodo
CREATE INDEX ix_entrega_estabelecimento_finalizada ON entrega (estabelecimento_id, finalizada_em);

-- Fila de revisao do despacho, em ordem de criacao
CREATE INDEX ix_entrega_estabelecimento_revisao ON entrega (estabelecimento_id, pendente_revisao, criado_em);

-- Polling do aplicativo: lote DESPACHADO ou EM_ROTA do entregador
CREATE INDEX ix_lote_motoboy_status ON lote_entrega (motoboy_id, status);

-- Recibos do dia da loja
CREATE INDEX ix_recibo_estabelecimento_data ON recibo (estabelecimento_id, data_referencia);
