package org.openlca.app.editors.graphical.model;

import java.util.Objects;

import org.openlca.app.editors.graphical.GraphEditor;
import org.openlca.app.util.Labels;
import org.openlca.core.model.Exchange;
import org.openlca.core.model.FlowType;
import org.openlca.core.model.ProcessLink;

public class ExchangeNode extends Node {

	public final Exchange exchange;

	ExchangeNode(GraphEditor editor, Exchange exchange) {
		super(editor);
		this.exchange = exchange;
	}

	public boolean isWaste() {
		if (exchange == null || exchange.flow == null)
			return false;
		return exchange.flow.flowType == FlowType.WASTE_FLOW;
	}

	public FlowType flowType() {
		return exchange == null || exchange.flow == null
				? null
				: exchange.flow.flowType;
	}

	@Override
	public String getName() {
		if (exchange == null) {
			return "";
		}
		return Labels.name(exchange.flow);
	}

	public boolean matches(ExchangeNode node) {
		if (node == null || this.exchange == null || node.exchange == null)
			return false;
		if (!Objects.equals(exchange.flow, node.exchange.flow))
			return false;
		return exchange.isInput != node.exchange.isInput;
	}

	public void setHighlighted(boolean value) {
		((ExchangeFigure) figure).setHighlighted(value);
	}

	@Override
	public ProcessNode parent() {
		return (ProcessNode) super.parent().parent();
	}

	/**
	 * Returns true if this exchange is connected.
	 */
	public boolean isConnected() {
		var parent = parent();
		if (parent == null || parent.process == null || exchange == null)
			return false;
		var linkSearch = parent.parent().linkSearch;
		var links = linkSearch.getConnectionLinks(parent.process.id);
		for (ProcessLink link : links) {
			if (link.exchangeId == exchange.id)
				return true;
		}
		return false;
	}

}
