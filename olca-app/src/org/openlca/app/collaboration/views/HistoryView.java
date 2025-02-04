package org.openlca.app.collaboration.views;

import java.util.ArrayList;

import org.eclipse.jface.viewers.IBaseLabelProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.part.ViewPart;
import org.openlca.app.M;
import org.openlca.app.collaboration.model.ActionType;
import org.openlca.app.collaboration.util.ObjectIds;
import org.openlca.app.collaboration.util.RefLabels;
import org.openlca.app.collaboration.viewers.HistoryViewer;
import org.openlca.app.collaboration.viewers.json.JsonDiffViewer;
import org.openlca.app.collaboration.viewers.json.olca.ModelDependencyResolver;
import org.openlca.app.collaboration.viewers.json.olca.ModelLabelProvider;
import org.openlca.app.collaboration.viewers.json.olca.ModelNodeBuilder;
import org.openlca.app.db.Repository;
import org.openlca.app.rcp.images.Images;
import org.openlca.app.rcp.images.Overlay;
import org.openlca.app.util.UI;
import org.openlca.app.viewers.BaseLabelProvider;
import org.openlca.app.viewers.tables.AbstractTableViewer;
import org.openlca.app.viewers.tables.Tables;
import org.openlca.git.model.Diff;
import org.openlca.git.model.DiffType;
import org.openlca.git.model.Reference;
import org.openlca.util.Strings;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class HistoryView extends ViewPart {

	public final static String ID = "views.collaboration.history";
	private static HistoryView instance;
	private HistoryViewer historyViewer;
	private AbstractTableViewer<Diff> referenceViewer;
	private JsonDiffViewer diffViewer;
	private Gson gson = new Gson();

	public HistoryView() {
		instance = this;
	}

	@Override
	public void createPartControl(Composite parent) {
		var body = new SashForm(parent, SWT.VERTICAL | SWT.SMOOTH);
		UI.gridData(body, true, true);
		UI.gridLayout(body, 1);
		createHistoryViewer(body);
		var secondRow = new SashForm(body, SWT.HORIZONTAL | SWT.SMOOTH);
		createDiffViewer(secondRow);
		createReferenceViewer(secondRow);
		refresh();
	}

	private void createHistoryViewer(Composite parent) {
		historyViewer = new HistoryViewer(parent);
		UI.gridData(historyViewer.getViewer().getTable(), true, true);
		Tables.bindColumnWidths(historyViewer.getViewer(), 0.1, 0.7, 0.1, 0.1);
		historyViewer.addSelectionChangedListener((commit) -> {
			referenceViewer.select(null);
			var diffs = Repository.isConnected() && commit != null
					? Repository.get().diffs.find().withPrevious(commit.id).all()
					: new ArrayList<Diff>();
			referenceViewer.setInput(diffs);
		});
	}

	private void createReferenceViewer(Composite parent) {
		referenceViewer = new AbstractTableViewer<>(parent) {
			@Override
			protected IBaseLabelProvider getLabelProvider() {
				return new ReferenceLabel();
			};
		};
		UI.gridData(referenceViewer.getViewer().getTable(), true, true);
		referenceViewer.addSelectionChangedListener((diff) -> {
			if (diff == null || !Repository.isConnected()) {
				diffViewer.setInput(null);
				return;
			}
			var currentElement = getJson(diff.right);
			var previousElement = getJson(diff.left);
			var node = new ModelNodeBuilder().build(currentElement, previousElement);
			diffViewer.setInput(node);
		});
	}

	private void createDiffViewer(Composite parent) {
		diffViewer = JsonDiffViewer.forViewing(parent, null, null);
		diffViewer.setLabels(M.SelectedCommit, M.PreviousCommit);
		diffViewer.initialize(new ModelLabelProvider(), ModelDependencyResolver.INSTANCE, ActionType.COMPARE_BEHIND);
	}

	private JsonObject getJson(Reference ref) {
		if (ref == null || ObjectIds.nullOrZero(ref.objectId))
			return null;
		var datasets = Repository.get().datasets;
		var json = datasets.get(ref.objectId);
		if (Strings.nullOrEmpty(json))
			return null;
		return gson.fromJson(json, JsonObject.class);
	}

	public static void refresh() {
		if (instance == null)
			return;
		instance.historyViewer.setRepository(Repository.get());
	}

	@Override
	public void dispose() {
		instance = null;
		super.dispose();
	}

	@Override
	public void setFocus() {

	}

	private class ReferenceLabel extends BaseLabelProvider {

		@Override
		public String getText(Object element) {
			if (!(element instanceof Diff))
				return null;
			var data = (Diff) element;
			return RefLabels.getFullName(data.ref());
		}

		@Override
		public Image getImage(Object element) {
			if (!(element instanceof Diff))
				return null;
			var data = (Diff) element;
			Overlay overlay = null;
			if (data.type == DiffType.ADDED) {
				overlay = Overlay.ADDED;
			} else if (data.type == DiffType.DELETED) {
				overlay = Overlay.DELETED;
			}
			return Images.get(data.ref().type, overlay);
		}

	}

}
