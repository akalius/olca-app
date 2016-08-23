package org.openlca.app.validation;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;
import org.openlca.app.App;
import org.openlca.app.db.Database;
import org.openlca.app.rcp.images.Icon;
import org.openlca.app.rcp.images.Images;
import org.openlca.app.util.UI;
import org.openlca.app.util.trees.Trees;
import org.openlca.app.util.viewers.Viewers;
import org.openlca.app.validation.ModelStatus.Status;
import org.openlca.core.database.CategoryDao;
import org.openlca.core.database.references.IReferenceSearch.Reference;
import org.openlca.core.model.Category;
import org.openlca.core.model.Parameter;
import org.openlca.core.model.descriptors.BaseDescriptor;
import org.openlca.core.model.descriptors.CategorizedDescriptor;

public class ValidationView extends ViewPart {

	private static ValidationView instance;
	private TreeViewer viewer;

	public ValidationView() {
		instance = this;
	}

	@Override
	public void createPartControl(Composite parent) {
		SashForm body = new SashForm(parent, SWT.VERTICAL | SWT.SMOOTH);
		UI.gridData(body, true, true);
		UI.gridLayout(body, 1);
		createViewer(body);
	}

	private void createViewer(Composite parent) {
		viewer = new TreeViewer(parent, SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI);
		viewer.setContentProvider(new ContentProvider());
		viewer.setLabelProvider(new StatusLabel());
		Tree tree = viewer.getTree();
		UI.gridData(tree, true, true);
		String[] columnHeaders = { "Description", "Path" };
		tree.setLinesVisible(true);
		tree.setHeaderVisible(true);
		for (String p : columnHeaders)
			new TreeColumn(tree, SWT.NULL).setText(p);
		viewer.setColumnProperties(columnHeaders);
		viewer.addDoubleClickListener((e) -> {
			Object element = Viewers.getFirst(e.getSelection());
			if (element == null || element instanceof StatusList)
				return;
			ModelStatus status = element instanceof ModelStatus ? (ModelStatus) element
					: ((StatusEntry) element).status;
			BaseDescriptor descriptor = Database.createRootDao(status.modelType).getDescriptor(status.id);
			App.openEditor(descriptor);
		});
		Trees.bindColumnWidths(tree, 0.5, 0.5);
	}

	public static void refresh() {
		IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
		try {
			ValidationView instance = (ValidationView) page.showView("views.problems");
			List<ModelStatus> result = new ArrayList<>();
			App.runWithProgress("Validating database", () -> {
				DatabaseValidation validation = new DatabaseValidation();
				result.addAll(validation.evaluateAll());
			});
			instance.viewer.setInput(createModel(result));
		} catch (PartInitException e) {
			e.printStackTrace();
		}
	}

	public static void clear() {
		if (instance == null)
			return;
		instance.viewer.setInput(new Object[0]);
	}

	@Override
	public void dispose() {
		instance = null;
		super.dispose();
	}

	@Override
	public void setFocus() {

	}

	private static StatusList[] createModel(List<ModelStatus> result) {
		StatusList warnings = new StatusList(Status.WARNING);
		StatusList errors = new StatusList(Status.ERROR);
		for (ModelStatus status : result) {
			ModelStatus onlyWarnings = new ModelStatus(status.modelType, status.id, filter(status.missing,
					Status.WARNING), true);
			ModelStatus onlyErrors = new ModelStatus(status.modelType, status.id, filter(status.missing,
					Status.ERROR), status.validReferenceSet);
			if (!onlyWarnings.missing.isEmpty())
				warnings.list.add(onlyWarnings);
			if (!onlyErrors.missing.isEmpty() || !status.validReferenceSet)
				errors.list.add(onlyErrors);
		}
		if (warnings.list.isEmpty() && errors.list.isEmpty())
			return new StatusList[0];
		if (warnings.list.isEmpty())
			return new StatusList[] { errors };
		if (errors.list.isEmpty())
			return new StatusList[] { warnings };
		return new StatusList[] { errors, warnings };
	}

	private static List<Reference> filter(List<Reference> initial, Status status) {
		List<Reference> filtered = new ArrayList<>();
		for (Reference ref : initial)
			if (ref.optional && status == Status.WARNING)
				filtered.add(ref);
			else if (!ref.optional && status == Status.ERROR)
				filtered.add(ref);
		return filtered;
	}

	private class StatusLabel extends LabelProvider implements ITableLabelProvider {

		@Override
		public Image getColumnImage(Object element, int column) {
			if (element instanceof StatusList)
				return getImage((StatusList) element, column);
			if (element instanceof ModelStatus)
				return getImage((ModelStatus) element, column);
			if (element instanceof StatusEntry)
				return getImage((StatusEntry) element, column);
			return null;
		}

		private Image getImage(StatusList list, int column) {
			if (column != 0)
				return null;
			if (list.status == Status.WARNING)
				return Icon.WARNING.get();
			return Icon.ERROR.get();
		}

		private Image getImage(ModelStatus status, int column) {
			if (column != 0)
				return null;
			return Images.get(status.modelType);
		}

		private Image getImage(StatusEntry entry, int column) {
			return null;
		}

		@Override
		public String getColumnText(Object element, int column) {
			if (element instanceof StatusList)
				return getText((StatusList) element, column);
			if (element instanceof ModelStatus)
				return getText((ModelStatus) element, column);
			if (element instanceof StatusEntry)
				return getText((StatusEntry) element, column);
			return null;
		}

		private String getText(StatusList list, int column) {
			if (column != 0)
				return null;
			int total = 0;
			for (ModelStatus status : list.list) {
				total += status.missing.size();
				if (!status.validReferenceSet)
					total += 1;
			}
			int models = list.list.size();
			if (list.status == Status.WARNING)
				return "#Warnings (" + total + " in " + models + " models)";
			return "#Errors (" + total + " in " + models + " models)";
		}

		private String getText(ModelStatus status, int column) {
			CategorizedDescriptor descriptor = Database.createRootDao(status.modelType).getDescriptor(status.id);
			Category category = null;
			if (descriptor.getCategory() != null)
				category = new CategoryDao(Database.get()).getForId(descriptor.getCategory());
			switch (column) {
			case 0:
				int count = status.missing.size();
				if (!status.validReferenceSet)
					count++;
				return descriptor.getName() + " (id=" + status.id + ") (" + count + ")";
			case 1:
				String text = "";
				while (category != null) {
					if (!text.isEmpty())
						text = "/" + text;
					text = category.getName() + text;
					category = category.getCategory();
				}
				return text;
			default:
				return null;
			}
		}

		private String getText(StatusEntry entry, int column) {
			if (column != 0)
				return null;
			Reference ref = entry.reference;
			if (ref == null)
				return "#No reference set";
			String text = "";
			if (ref.id == 0)
				text = "#Missing ";
			else
				text = "#Broken ";
			if (ref.getType() == Parameter.class) {
				return text += "parameter '" + ref.property + "' in formula";
			} else {
				text += ref.property;
				if (ref.id != 0)
					text += " (id=" + ref.id + ")";
				if (!com.google.common.base.Strings.isNullOrEmpty(ref.nestedProperty))
					text += " in " + ref.nestedProperty + " (id=" + ref.nestedOwnerId + ")";
				return text;
			}
		}
	}

	private class ContentProvider implements ITreeContentProvider {

		@Override
		public Object[] getElements(Object inputElement) {
			if (inputElement == null)
				return new Object[0];
			return (Object[]) inputElement;
		}

		@Override
		public Object[] getChildren(Object parentElement) {
			if (parentElement instanceof StatusList)
				return ((StatusList) parentElement).list.toArray();
			if (parentElement instanceof ModelStatus) {
				List<StatusEntry> texts = new ArrayList<>();
				ModelStatus status = (ModelStatus) parentElement;
				for (Reference ref : status.missing)
					texts.add(new StatusEntry(status, ref));
				if (!status.validReferenceSet)
					texts.add(new StatusEntry(status, null));
				return texts.toArray();
			}
			return new Object[0];
		}

		@Override
		public Object getParent(Object element) {
			return null;
		}

		@Override
		public boolean hasChildren(Object element) {
			if (element instanceof StatusList)
				return true;
			if (element instanceof ModelStatus)
				return true;
			return false;
		}

		@Override
		public void dispose() {
		}

		@Override
		public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		}

	}

	private static class StatusList {
		private final Status status;
		private final List<ModelStatus> list = new ArrayList<>();

		private StatusList(Status status) {
			this.status = status;
		}
	}

	private static class StatusEntry {
		private final ModelStatus status;
		private final Reference reference;

		private StatusEntry(ModelStatus status, Reference reference) {
			this.status = status;
			this.reference = reference;
		}
	}

}
