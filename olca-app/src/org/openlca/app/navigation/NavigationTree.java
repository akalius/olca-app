package org.openlca.app.navigation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jface.viewers.ColumnViewerToolTipSupport;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.openlca.app.navigation.elements.INavigationElement;
import org.openlca.app.navigation.elements.LibraryElement;
import org.openlca.app.navigation.elements.ModelTypeElement;
import org.openlca.core.model.ModelType;

/**
 * Factory methods for creating navigation trees in the application.
 */
public class NavigationTree {

	/**
	 * Creates a tree viewer for the selection of single models of the given type.
	 * The input of the respective navigation element is already done in this
	 * method.
	 */
	public static TreeViewer forSingleSelection(Composite parent, ModelType type) {
		TreeViewer viewer = createViewer(parent, SWT.SINGLE);
		viewer.setInput(contentOf(type));
		return viewer;
	}

	/**
	 * Creates a tree viewer for the selection of multiple models of the given type.
	 * The input of the respective navigation element is already done in this
	 * method.
	 */
	public static TreeViewer forMultiSelection(Composite parent, ModelType type) {
		TreeViewer viewer = createViewer(parent, SWT.MULTI);
		viewer.setInput(contentOf(type));
		return viewer;
	}

	private static TreeViewer createViewer(Composite parent, int selection) {
		var viewer = new TreeViewer(parent, SWT.BORDER | selection);
		viewer.setContentProvider(new NavigationContentProvider());
		viewer.setLabelProvider(new NavigationLabelProvider(false));
		viewer.setComparator(new NavigationComparator());
		ColumnViewerToolTipSupport.enableFor(viewer);
		return viewer;
	}

	private static List<INavigationElement<?>> contentOf(ModelType type) {
		if (type == null)
			return Collections.emptyList();
		var root = Navigator.getNavigationRoot();
		if (root == null)
			return Collections.emptyList();
		var queue = new ArrayDeque<INavigationElement<?>>();
		queue.add(root);
		var coll = new ArrayList<INavigationElement<?>>();
		while (!queue.isEmpty()) {
			var next = queue.poll();
			if (next instanceof ModelTypeElement elem) {
				if (elem.getContent() == type) {
					var lib = elem.getLibrary();
					coll.add(lib.isPresent()
							? LibraryElement.of(lib.get(), type)
							: elem);
				}
				continue;
			}
			queue.addAll(next.getChildren());
		}

		// if there is only one element in the collection and
		// if it is a model type element, return the content
		// of that element, otherwise we have active libraries
		// in the tree and we want to show the content of these
		// libraries in a structured way

		if (coll.size() != 1)
			return coll;
		var first = coll.get(0);
		return first instanceof ModelTypeElement
				? first.getChildren()
				: coll;
	}

}
