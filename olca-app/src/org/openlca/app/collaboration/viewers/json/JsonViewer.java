package org.openlca.app.collaboration.viewers.json;

import java.util.List;

import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.openlca.app.collaboration.dialogs.TextDiffDialog;
import org.openlca.app.collaboration.model.ActionType;
import org.openlca.app.collaboration.viewers.json.content.JsonContentProvider;
import org.openlca.app.collaboration.viewers.json.content.JsonNode;
import org.openlca.app.collaboration.viewers.json.label.IJsonNodeLabelProvider;
import org.openlca.app.collaboration.viewers.json.label.JsonLabelProvider;
import org.openlca.app.collaboration.viewers.json.listener.ExpansionListener;
import org.openlca.app.collaboration.viewers.json.listener.ScrollListener;
import org.openlca.app.collaboration.viewers.json.listener.SelectionChangedListener;
import org.openlca.app.util.UI;
import org.openlca.app.viewers.AbstractViewer;
import org.openlca.app.viewers.Viewers;

public class JsonViewer extends AbstractViewer<JsonNode, TreeViewer> {

	private Side side;
	private ActionType action;

	public JsonViewer(Composite parent, Side side, ActionType action) {
		super(parent, side);
		this.side = side;
		this.action = action;
	}

	@Override
	public TreeViewer getViewer() {
		return super.getViewer();
	}

	public void setCounterpart(JsonViewer counterpart) {
		var viewer = counterpart.getViewer();
		getViewer().addTreeListener(new ExpansionListener(viewer));
		getViewer().addSelectionChangedListener(new SelectionChangedListener(viewer));
		var vBar = getViewer().getTree().getVerticalBar();
		vBar.addSelectionListener(new ScrollListener(viewer));
	}

	public void setLabelProvider(IJsonNodeLabelProvider labelProvider) {
		getViewer().setLabelProvider(new JsonLabelProvider(labelProvider, side));
	}

	@Override
	protected TreeViewer createViewer(Composite parent) {
		var viewer = new TreeViewer(parent, SWT.MULTI | SWT.NO_FOCUS | SWT.HIDE_SELECTION | SWT.BORDER);
		viewer.setContentProvider(new JsonContentProvider());
		var tree = viewer.getTree();
		if (viewerParameters[0] == Side.LOCAL) {
			tree.getVerticalBar().setVisible(false);
		}
		UI.gridData(tree, true, true);
		viewer.addDoubleClickListener((e) -> onDoubleClick(e));
		return viewer;
	}

	private void onDoubleClick(DoubleClickEvent e) {
		var sel = (IStructuredSelection) e.getSelection();
		var node = (JsonNode) sel.getFirstElement();
		if (!node.element().isJsonPrimitive())
			return;
		new TextDiffDialog(node, action).open();
	}

	public List<JsonNode> getSelection() {
		return Viewers.getAllSelected(getViewer());
	}

	@Override
	public void setInput(JsonNode[] input) {
		super.setInput(input);
	}

}