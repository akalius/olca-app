package org.openlca.app.navigation;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;

import org.openlca.app.db.Database;
import org.openlca.app.db.DatabaseDir;
import org.openlca.app.navigation.elements.CategoryElement;
import org.openlca.app.navigation.elements.INavigationElement;
import org.openlca.app.navigation.elements.ModelElement;
import org.openlca.app.navigation.elements.ModelTypeElement;
import org.openlca.core.database.CategorizedEntityDao;
import org.openlca.core.database.CategoryDao;
import org.openlca.core.database.Daos;
import org.openlca.core.model.CategorizedEntity;
import org.openlca.core.model.Category;
import org.openlca.core.model.ModelType;
import org.openlca.core.model.descriptors.CategorizedDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CopyPaste {

	private enum Action {
		NONE, COPY, CUT
	}

	private static INavigationElement<?>[] cache = null;
	private static Action currentAction = Action.NONE;

	public static void copy(Collection<INavigationElement<?>> elements) {
		copy(elements.toArray(new INavigationElement<?>[0]));
	}

	public static void copy(INavigationElement<?>[] elements) {
		if (!isSupported(elements))
			return;
		initialize(Action.COPY, elements);
	}

	public static void cut(Collection<INavigationElement<?>> elements) {
		cut(elements.toArray(new INavigationElement<?>[0]));
	}

	private static void cut(INavigationElement<?>[] elements) {
		if (!isSupported(elements))
			return;
		initialize(Action.CUT, elements);
		var navigator = Navigator.getInstance();
		for (var elem : cache) {
			elem.getParent().getChildren().remove(elem);
			if (navigator != null) {
				navigator.getCommonViewer().refresh(elem.getParent());
			}
		}
	}

	public static boolean isSupported(INavigationElement<?> elem) {
		if (!(elem instanceof ModelElement)
				&& !(elem instanceof CategoryElement))
			return false;
		return elem.getLibrary().isEmpty();
	}

	public static boolean isSupported(Collection<INavigationElement<?>> elements) {
		if (elements == null)
			return false;
		return isSupported(elements.toArray(new INavigationElement[0]));
	}

	public static boolean isSupported(INavigationElement<?>[] elements) {
		if (elements == null || elements.length == 0)
			return false;
		ModelType modelType = null;
		for (INavigationElement<?> element : elements) {
			if (!isSupported(element))
				return false;
			ModelType currentModelType = getModelType(element);
			if (modelType == null)
				modelType = currentModelType;
			else if (currentModelType != modelType)
				return false;
		}
		return true;
	}

	private static ModelType getModelType(INavigationElement<?> e) {
		if (e instanceof ModelElement)
			return ((ModelElement) e).getContent().type;
		if (e instanceof CategoryElement)
			return ((CategoryElement) e).getContent().modelType;
		if (e instanceof ModelTypeElement)
			return ((ModelTypeElement) e).getContent();
		return ModelType.UNKNOWN;
	}

	private static void initialize(Action action, INavigationElement<?>[] elements) {
		if (action == Action.CUT && currentAction == Action.CUT) {
			extendCache(elements);
			return;
		}
		if (currentAction == Action.CUT)
			restore();
		cache = elements;
		currentAction = action;
	}

	private static void extendCache(INavigationElement<?>[] elements) {
		if (elements == null || elements.length == 0)
			return;
		if (cacheIsEmpty()) {
			cache = elements;
			return;
		}
		INavigationElement<?>[] newCache = new INavigationElement<?>[cache.length + elements.length];
		System.arraycopy(cache, 0, newCache, 0, cache.length);
		System.arraycopy(elements, 0, newCache, cache.length, elements.length);
		cache = newCache;
	}

	private static void restore() {
		if (cacheIsEmpty())
			return;
		for (INavigationElement<?> element : cache) {
			paste(element, element.getParent());
			INavigationElement<?> root = Navigator.findElement(getModelType(element));
			Navigator.refresh(root);
		}
	}

	public static void pasteTo(INavigationElement<?> categoryElement) {
		if (cacheIsEmpty())
			return;
		if (!canPasteTo(categoryElement))
			return;
		boolean started = false;
		try {
			Database.getWorkspaceIdUpdater().beginTransaction();
			started = true;
			for (INavigationElement<?> element : cache)
				paste(element, categoryElement);
		} finally {
			if (started)
				Database.getWorkspaceIdUpdater().endTransaction();
			clearCache();
		}
	}

	public static void clearCache() {
		cache = null;
		currentAction = Action.NONE;
		INavigationElement<?> root = Navigator.findElement(Database.getActiveConfiguration());
		Navigator.refresh(root);
	}

	public static boolean canMove(Collection<INavigationElement<?>> elements, INavigationElement<?> target) {
		return canMove(elements.toArray(new INavigationElement[0]), target);
	}

	private static boolean canMove(INavigationElement<?>[] elements, INavigationElement<?> target) {
		if (!isSupported(elements))
			return false;
		if (!(target instanceof CategoryElement || target instanceof ModelTypeElement))
			return false;
		return getModelType(target) == getModelType(elements[0]);
	}

	public static boolean canPasteTo(INavigationElement<?> element) {
		if (cacheIsEmpty())
			return false;
		if (!(element instanceof CategoryElement || element instanceof ModelTypeElement))
			return false;
		return getModelType(element) == getModelType(cache[0]);
	}

	private static void paste(INavigationElement<?> element, INavigationElement<?> category) {
		if (currentAction == Action.CUT) {
			if (element instanceof CategoryElement)
				move((CategoryElement) element, category);
			else if (element instanceof ModelElement)
				move((ModelElement) element, category);
		} else if (currentAction == Action.COPY) {
			if (element instanceof CategoryElement)
				copy((CategoryElement) element, category);
			else if (element instanceof ModelElement)
				copy((ModelElement) element, category);
		}
	}

	private static void copy(ModelElement element, INavigationElement<?> categoryElement) {
		CategorizedEntity copy = copy(element);
		if (copy == null)
			return;
		copy.category = getCategory(categoryElement);
		insert(copy);
	}

	private static Category getCategory(INavigationElement<?> element) {
		return element instanceof CategoryElement ? ((CategoryElement) element).getContent() : null;
	}

	private static void move(CategoryElement element, INavigationElement<?> categoryElement) {
		Category newParent = getCategory(categoryElement);
		Category oldParent = getCategory(element.getParent());
		Category category = element.getContent();
		if (Objects.equals(category, newParent))
			return;
		if (isChild(newParent, category))
			return; // do not create category cycles
		if (oldParent != null)
			oldParent.childCategories.remove(category);
		if (newParent != null)
			newParent.childCategories.add(category);
		category.category = newParent;
		CategoryDao dao = new CategoryDao(Database.get());
		if (oldParent != null) {
			dao.update(oldParent);
		}
		if (newParent != null) {
			dao.update(newParent);
		}
		dao.update(category);
	}

	private static boolean isChild(Category category, Category parent) {
		if (category == null || parent == null)
			return false;
		Category p = category.category;
		while (p != null) {
			if (Objects.equals(p, parent))
				return true;
			p = p.category;
		}
		return false;
	}

	private static void move(ModelElement element, INavigationElement<?> categoryElement) {
		CategorizedDescriptor entity = element.getContent();
		Category category = getCategory(categoryElement);
		Optional<Category> parent = Optional.ofNullable(category);
		Daos.categorized(Database.get(), entity.type).updateCategory(entity, parent);
	}

	private static void copy(CategoryElement element, INavigationElement<?> category) {
		Category parent = getCategory(category);
		Queue<CategoryElement> elements = new LinkedList<>();
		elements.add(element);
		while (!elements.isEmpty()) {
			CategoryElement current = elements.poll();
			Category copy = current.getContent().copy();
			copy.childCategories.clear();
			copy.category = parent;
			if (parent == null)
				copy = new CategoryDao(Database.get()).insert(copy);
			else {
				parent.childCategories.add(copy);
				copy = new CategoryDao(Database.get()).update(parent);
			}
			for (INavigationElement<?> child : current.getChildren())
				if (child instanceof CategoryElement)
					elements.add((CategoryElement) child);
				else {
					CategorizedEntity modelCopy = copy((ModelElement) child);
					modelCopy.category = copy;
					insert(modelCopy);
				}
			parent = copy;
		}
	}

	private static CategorizedEntity copy(ModelElement element) {
		CategorizedDescriptor descriptor = element.getContent();
		CategorizedEntityDao<?, ?> dao = Daos.categorized(Database.get(), descriptor.type);
		CategorizedEntity entity = dao.getForId(descriptor.id);
		CategorizedEntity copy = cloneIt(entity);
		if (copy != null)
			copy.name = copy.name + " (copy)";
		return copy;
	}

	private static CategorizedEntity cloneIt(CategorizedEntity entity) {
		try {
			CategorizedEntity clone = (CategorizedEntity) entity.copy();
			DatabaseDir.copyDir(entity, clone);
			return clone;
		} catch (Exception e) {
			Logger log = LoggerFactory.getLogger(CopyPaste.class);
			log.error("failed to clone " + entity, e);
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	private static <T extends CategorizedEntity> void insert(T entity) {
		Class<T> clazz = (Class<T>) entity.getClass();
		Daos.base(Database.get(), clazz).insert(entity);
	}

	public static boolean cacheIsEmpty() {
		return cache == null || cache.length == 0;
	}
}
