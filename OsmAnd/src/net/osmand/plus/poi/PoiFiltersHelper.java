package net.osmand.plus.poi;

import static net.osmand.plus.poi.PoiUIFilter.TOP_WIKI_FILTER_ID;

import android.util.ArraySet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import net.osmand.PlatformUtil;
import net.osmand.osm.AbstractPoiType;
import net.osmand.osm.MapPoiTypes;
import net.osmand.osm.PoiCategory;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.api.SQLiteAPI.SQLiteConnection;
import net.osmand.plus.api.SQLiteAPI.SQLiteCursor;
import net.osmand.plus.api.SQLiteAPI.SQLiteStatement;
import net.osmand.plus.backup.BackupUtils;
import net.osmand.plus.plugins.PluginsHelper;
import net.osmand.plus.settings.backend.ApplicationMode;
import net.osmand.search.core.TopIndexFilter;
import net.osmand.plus.settings.backend.OsmandSettings;
import net.osmand.util.CollectionUtils;

import org.apache.commons.logging.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public class PoiFiltersHelper {

	private static final Log LOG = PlatformUtil.getLog(PoiFiltersHelper.class);
	private final OsmandApplication app;
	private final OsmandSettings settings;

	private NominatimPoiFilter nominatimPOIFilter;
	private NominatimPoiFilter nominatimAddressFilter;

	private PoiUIFilter searchByNamePOIFilter;
	private PoiUIFilter customPOIFilter;
	private PoiUIFilter showAllPOIFilter;
	private PoiUIFilter topWikiPoiFilter;
	private List<PoiUIFilter> cacheTopStandardFilters = null;
	private Set<PoiUIFilter> overwrittenSelectedPoiFilters = new TreeSet<>();
	private Set<PoiUIFilter> selectedPoiFilters = new TreeSet<>();
	private boolean useOverwrittenFilters;

	private static final String UDF_CAR_AID = "car_aid";
	private static final String UDF_FOR_TOURISTS = "for_tourists";
	private static final String UDF_FOOD_SHOP = "food_shop";
	private static final String UDF_FUEL = "fuel";
	private static final String UDF_SIGHTSEEING = "sightseeing";
	private static final String UDF_EMERGENCY = "emergency";
	private static final String UDF_PUBLIC_TRANSPORT = "public_transport";
	private static final String UDF_ACCOMMODATION = "accommodation";
	private static final String UDF_RESTAURANTS = "restaurants";
	private static final String UDF_PARKING = "parking";

	private static final String[] DEL = {
			UDF_CAR_AID, UDF_FOR_TOURISTS, UDF_FOOD_SHOP, UDF_FUEL, UDF_SIGHTSEEING, UDF_EMERGENCY,
			UDF_PUBLIC_TRANSPORT, UDF_ACCOMMODATION, UDF_RESTAURANTS, UDF_PARKING
	};
	
	public PoiFiltersHelper(OsmandApplication app) {
		this.app = app;
		this.settings = app.getSettings();
		PoiFilterDbHelper helper = openDbHelperNoPois();
		helper.doDeletion();
		helper.close();
	}

	public long getLastModifiedTime() {
		PoiFilterDbHelper helper = openDbHelperNoPois();
		long lastModifiedTime = helper.getLastModifiedTime();
		helper.close();
		return lastModifiedTime;
	}

	public void setLastModifiedTime(long lastModifiedTime) {
		PoiFilterDbHelper helper = openDbHelperNoPois();
		helper.setLastModifiedTime(lastModifiedTime);
		helper.close();
	}

	public NominatimPoiFilter getNominatimPOIFilter() {
		if (nominatimPOIFilter == null) {
			nominatimPOIFilter = new NominatimPoiFilter(app, false);
		}
		return nominatimPOIFilter;
	}

	public NominatimPoiFilter getNominatimAddressFilter() {
		if (nominatimAddressFilter == null) {
			nominatimAddressFilter = new NominatimPoiFilter(app, true);
		}
		return nominatimAddressFilter;
	}

	public void resetNominatimFilters() {
		nominatimPOIFilter = null;
		nominatimAddressFilter = null;
	}

	public PoiUIFilter getSearchByNamePOIFilter() {
		if (searchByNamePOIFilter == null) {
			PoiUIFilter filter = new SearchByNameFilter(app);
			filter.setStandardFilter(true);
			searchByNamePOIFilter = filter;
		}
		return searchByNamePOIFilter;
	}

	public PoiUIFilter getCustomPOIFilter() {
		if (customPOIFilter == null) {
			PoiUIFilter filter = new PoiUIFilter(app.getString(R.string.poi_filter_custom_filter),
					PoiUIFilter.CUSTOM_FILTER_ID, new LinkedHashMap<>(), app);
			filter.setStandardFilter(true);
			customPOIFilter = filter;
		}
		return customPOIFilter;
	}

	@Nullable
	public PoiUIFilter getTopWikiPoiFilter() {
		if (topWikiPoiFilter == null) {
			topWikiPoiFilter = PluginsHelper.getPoiFilterById(TOP_WIKI_FILTER_ID);
		}
		return topWikiPoiFilter;
	}

	public PoiUIFilter getShowAllPOIFilter() {
		if (showAllPOIFilter == null) {
			PoiUIFilter filter = new PoiUIFilter(null, app, "");
			filter.setStandardFilter(true);
			showAllPOIFilter = filter;
		}
		return showAllPOIFilter;
	}

	public void markHistory(String filterId, boolean history) {
		PoiFilterDbHelper helper = openDbHelperNoPois();
		helper.markHistory(filterId, history);
		helper.close();
	}

	public void clearHistory() {
		PoiFilterDbHelper helper = openDbHelperNoPois();
		helper.clearHistory();
		helper.close();
	}

	@Nullable
	private PoiUIFilter getFilterById(String filterId, PoiUIFilter... filters) {
		for (PoiUIFilter pf : filters) {
			if (pf != null && pf.getFilterId() != null && pf.getFilterId().equals(filterId)) {
				return pf;
			}
		}
		return null;
	}

	public PoiUIFilter getFilterById(String filterId) {
		return getFilterById(filterId, false);
	}

	@Nullable
	public PoiUIFilter getFilterById(String filterId, boolean includeDeleted) {
		if (filterId == null) {
			return null;
		}
		for (PoiUIFilter filter : getTopDefinedPoiFilters(includeDeleted)) {
			if (filter.getFilterId().equals(filterId)) {
				return filter;
			}
		}
		PoiUIFilter filter = getFilterById(filterId, getCustomPOIFilter(), getSearchByNamePOIFilter(),
				getTopWikiPoiFilter(), getShowAllPOIFilter(), getNominatimPOIFilter(), getNominatimAddressFilter());
		if (filter != null) {
			return filter;
		}
		if (filterId.startsWith(PoiUIFilter.STD_PREFIX)) {
			String typeId = filterId.substring(PoiUIFilter.STD_PREFIX.length());
			AbstractPoiType tp = app.getPoiTypes().getAnyPoiTypeByKey(typeId);
			if (tp != null) {
				PoiUIFilter lf = new PoiUIFilter(tp, app, "");
				return addTopPoiFilter(lf);
			}
			AbstractPoiType lt = app.getPoiTypes().getAnyPoiAdditionalTypeByKey(typeId);
			if (lt != null) {
				PoiUIFilter lf = new PoiUIFilter(lt, app, "");
				return addTopPoiFilter(lf);
			}
		}
		return null;
	}

	@Nullable
	public PoiUIFilter getFilter(TopIndexFilter topIndexFilter, Map<PoiCategory, LinkedHashSet<String>> acceptedTypes) {
		PoiUIFilter poiUIFilter = new PoiUIFilter(topIndexFilter, acceptedTypes, app);
		return addTopPoiFilter(poiUIFilter);
	}

	public void reloadAllPoiFilters() {
		showAllPOIFilter = null;
		getShowAllPOIFilter();
		setTopStandardFilters(null);
		getTopStandardFilters();
	}

	public List<PoiUIFilter> getUserDefinedPoiFilters(boolean includeDeleted) {
		ArrayList<PoiUIFilter> userDefinedFilters = new ArrayList<>();
		PoiFilterDbHelper helper = openDbHelper();
		if (helper != null) {
			List<PoiUIFilter> userDefined = helper.getFilters(helper.getReadableDatabase(), includeDeleted);
			userDefinedFilters.addAll(userDefined);
			helper.close();
		}
		return userDefinedFilters;
	}

	public List<PoiUIFilter> getSearchPoiFilters() {
		List<PoiUIFilter> result = new ArrayList<>();
		List<PoiUIFilter> filters = Arrays.asList(getCustomPOIFilter(),  // getShowAllPOIFilter(),
				getSearchByNamePOIFilter(), getNominatimPOIFilter(), getNominatimAddressFilter());
		for (PoiUIFilter f : filters) {
			if (f != null && !f.isEmpty()) {
				result.add(f);
			}
		}
		return result;
	}

	@NonNull
	public List<PoiUIFilter> getTopDefinedPoiFilters() {
		return getTopDefinedPoiFilters(false);
	}

	@NonNull
	public List<PoiUIFilter> getTopDefinedPoiFilters(boolean includeDeleted) {
		List<PoiUIFilter> result = new ArrayList<>();
		List<PoiUIFilter> standardFilters = getTopStandardFilters();
		if (standardFilters != null) {
			for (PoiUIFilter filter : standardFilters) {
				if (includeDeleted || !filter.isDeleted()) {
					result.add(filter);
				}
			}
			result.add(getShowAllPOIFilter());
		}
		return result;
	}

	@Nullable
	private List<PoiUIFilter> getTopStandardFilters() {
		// collect top standard filters if poi types are initialized
		MapPoiTypes poiTypes = app.getPoiTypes();
		if (cacheTopStandardFilters == null && poiTypes.isInit()) {
			// user defined
			List<PoiUIFilter> filters = getUserDefinedPoiFilters(true);
			// default
			for (AbstractPoiType t : poiTypes.getTopVisibleFilters()) {
				filters.add(new PoiUIFilter(t, app, ""));
			}
			PluginsHelper.registerPoiFilters(filters);
			cacheTopStandardFilters = filters;
		}
		return cacheTopStandardFilters;
	}
	
	private void setTopStandardFilters(@Nullable List<PoiUIFilter> topStandardFilters) {
		this.cacheTopStandardFilters = topStandardFilters;
	}

	public List<String> getPoiFilterOrders(boolean onlyActive) {
		List<String> filterOrders = new ArrayList<>();
		for (PoiUIFilter filter : getSortedPoiFilters(onlyActive)) {
			filterOrders.add(filter.getFilterId());
		}
		return filterOrders;
	}

	public List<PoiUIFilter> getSortedPoiFilters(boolean onlyActive) {
		ApplicationMode selectedAppMode = settings.getApplicationMode();
		return getSortedPoiFilters(selectedAppMode, onlyActive);
	}

	public List<PoiUIFilter> getSortedPoiFilters(@NonNull ApplicationMode appMode, boolean onlyActive) {
		initPoiUIFiltersState(appMode);
		List<PoiUIFilter> allFilters = new ArrayList<>();
		allFilters.addAll(getTopDefinedPoiFilters());
		allFilters.addAll(getSearchPoiFilters());
		Collections.sort(allFilters);
		if (onlyActive) {
			List<PoiUIFilter> onlyActiveFilters = new ArrayList<>();
			for (PoiUIFilter f : allFilters) {
				if (f.isActive()) {
					onlyActiveFilters.add(f);
				}
			}
			return onlyActiveFilters;
		} else {
			return allFilters;
		}
	}
	
	private void initPoiUIFiltersState(@NonNull ApplicationMode appMode) {
		List<PoiUIFilter> allFilters = new ArrayList<>();
		allFilters.addAll(getTopDefinedPoiFilters());
		allFilters.addAll(getSearchPoiFilters());

		refreshPoiFiltersActivation(appMode, allFilters);
		refreshPoiFiltersOrder(appMode, allFilters);
		
		//set up the biggest order to custom filter
		PoiUIFilter customFilter = getCustomPOIFilter();
		customFilter.setActive(true);
		customFilter.setOrder(allFilters.size());
	}
	
	private void refreshPoiFiltersOrder(@NonNull ApplicationMode appMode,
	                                    List<PoiUIFilter> filters) {
		Map<String, Integer> orders = getPoiFiltersOrder(appMode);
		List<PoiUIFilter> existedFilters = new ArrayList<>();
		List<PoiUIFilter> newFilters = new ArrayList<>();
		if (orders != null) {
			//set up orders from settings
			for (PoiUIFilter filter : filters) {
				Integer order = orders.get(filter.getFilterId());
				if (order != null) {
					filter.setOrder(order);
					existedFilters.add(filter);
				} else {
					newFilters.add(filter);
				}
			}
			//make order values without spaces
			Collections.sort(existedFilters);
			for (int i = 0; i < existedFilters.size(); i++) {
				existedFilters.get(i).setOrder(i);
			}
			//set up maximum orders for new poi filters
			Collections.sort(newFilters);
			for (PoiUIFilter filter : newFilters) {
				filter.setOrder(existedFilters.size());
				existedFilters.add(filter);
			}
		} else {
			for (PoiUIFilter filter : filters) {
				filter.setOrder(PoiUIFilter.INVALID_ORDER);
			}
		}
	}
	
	private void refreshPoiFiltersActivation(@NonNull ApplicationMode appMode,
	                                         List<PoiUIFilter> filters) {
		List<String> inactiveFiltersIds = getInactivePoiFiltersIds(appMode);
		if (inactiveFiltersIds != null) {
			for (PoiUIFilter filter : filters) {
				filter.setActive(!inactiveFiltersIds.contains(filter.getFilterId()));
			}
		} else {
			for (PoiUIFilter filter : filters) {
				filter.setActive(true);
			}
		}
	}

	public void saveFiltersOrder(ApplicationMode appMode, List<String> filterIds) {
		settings.POI_FILTERS_ORDER.setStringsListForProfile(appMode, filterIds);
	}

	public void saveInactiveFilters(ApplicationMode appMode, List<String> filterIds) {
		settings.INACTIVE_POI_FILTERS.setStringsListForProfile(appMode, filterIds);
	}

	public Map<String, Integer> getPoiFiltersOrder(@NonNull ApplicationMode appMode) {
		List<String> ids = settings.POI_FILTERS_ORDER.getStringsListForProfile(appMode);
		if (ids == null) {
			return null;
		}
		Map<String, Integer> result = new HashMap<>();
		for (int i = 0; i < ids.size(); i++) {
			result.put(ids.get(i), i);
		}
		return result;
	}
	
	public List<String> getInactivePoiFiltersIds(@NonNull ApplicationMode appMode) {
		return settings.INACTIVE_POI_FILTERS.getStringsListForProfile(appMode);
	}

	private PoiFilterDbHelper openDbHelperNoPois() {
		return new PoiFilterDbHelper(null, app);
	}

	private PoiFilterDbHelper openDbHelper() {
		if (!app.getPoiTypes().isInit()) {
			return null;
		}
		return new PoiFilterDbHelper(app.getPoiTypes(), app);
	}

	public boolean removePoiFilter(PoiUIFilter filter) {
		if (filter.isCustomPoiFilter() ||
				filter.getFilterId().equals(PoiUIFilter.BY_NAME_FILTER_ID) ||
				filter.getFilterId().startsWith(PoiUIFilter.STD_PREFIX)) {
			return false;
		}
		PoiFilterDbHelper helper = openDbHelper();
		if (helper == null) {
			return false;
		}
		boolean res = helper.deleteFilter(helper.getWritableDatabase(), filter, false);
		helper.close();
		return res;
	}

	public boolean createPoiFilter(@NonNull PoiUIFilter filter, boolean forHistory) {
		PoiFilterDbHelper helper = openDbHelper();
		List<PoiUIFilter> standardFilters = getTopStandardFilters();
		if (standardFilters == null || helper == null) {
			return false;
		}
		helper.deleteFilter(helper.getWritableDatabase(), filter, true);

		Set<PoiUIFilter> filtersToRemove = new HashSet<>();
		for (PoiUIFilter f : standardFilters) {
			if (Objects.equals(f.getFilterId(), filter.getFilterId())) {
				filtersToRemove.add(f);
			}
		}
		setTopStandardFilters(standardFilters = CollectionUtils.removeAllFromList(standardFilters, filtersToRemove));
		boolean res = helper.addFilter(filter, helper.getWritableDatabase(), false, forHistory);
		if (res) {
			addTopPoiFilter(filter);
			Collections.sort(standardFilters);
		}
		helper.close();
		return res;
	}

	public boolean editPoiFilter(PoiUIFilter filter) {
		if (filter.isCustomPoiFilter()
				|| filter.getFilterId().equals(PoiUIFilter.BY_NAME_FILTER_ID)
				|| filter.getFilterId().startsWith(PoiUIFilter.STD_PREFIX)) {
			return false;
		}
		PoiFilterDbHelper helper = openDbHelper();
		if (helper != null) {
			boolean res = helper.editFilter(helper.getWritableDatabase(), filter);
			helper.close();
			return res;
		}
		return false;
	}

	@NonNull
	public Set<PoiUIFilter> getGeneralSelectedPoiFilters() {
		Set<PoiUIFilter> selectedPoiFilters = getSelectedPoiFilters();
		PoiUIFilter wiki = getTopWikiPoiFilter();
		if (isPoiFilterSelected(wiki)) {
			Set<PoiUIFilter> result = new TreeSet<>(selectedPoiFilters);
			result.remove(wiki);
			return result;
		}
		return selectedPoiFilters;
	}

	@NonNull
	public Set<PoiUIFilter> getSelectedPoiFilters() {
		return useOverwrittenFilters ? overwrittenSelectedPoiFilters : selectedPoiFilters;
	}

	public void replaceSelectedPoiFilters(PoiUIFilter filter) {
		Set<PoiUIFilter> overwrittenSelectedPoiFilters = new TreeSet<>();
		overwrittenSelectedPoiFilters.add(filter);
		PoiUIFilter wiki = getTopWikiPoiFilter();
		if (isPoiFilterSelected(wiki)) {
			overwrittenSelectedPoiFilters.add(wiki);
		}
		this.overwrittenSelectedPoiFilters = overwrittenSelectedPoiFilters;
		useOverwrittenFilters = true;
	}

	public void restoreSelectedPoiFilters() {
		PoiUIFilter wiki = getTopWikiPoiFilter();
		if (wiki != null) {
			Set<PoiUIFilter> selectedPoiFilters = new TreeSet<>(this.selectedPoiFilters);
			if (isPoiFilterSelected(wiki)) {
				selectedPoiFilters.add(wiki);
			} else {
				selectedPoiFilters.remove(wiki);
			}
			this.selectedPoiFilters = selectedPoiFilters;
		}
		useOverwrittenFilters = false;
	}

	public void addSelectedPoiFilter(PoiUIFilter filter) {
		if (filter != null) {
			Set<PoiUIFilter> selectedPoiFilters = new TreeSet<>(getSelectedPoiFilters());
			selectedPoiFilters.add(filter);
			PluginsHelper.onPrepareExtraTopPoiFilters(selectedPoiFilters);
			setSelectedPoiFilters(selectedPoiFilters);
		}
	}

	public void removeSelectedPoiFilter(PoiUIFilter filter) {
		if (filter != null) {
			Set<PoiUIFilter> selectedPoiFilters = new TreeSet<>(getSelectedPoiFilters());
			selectedPoiFilters.remove(filter);
			setSelectedPoiFilters(selectedPoiFilters);
		}
	}

	private PoiUIFilter addTopPoiFilter(@NonNull PoiUIFilter filter) {
		List<PoiUIFilter> standardFilters = getTopStandardFilters();
		if (standardFilters != null) {
			setTopStandardFilters(CollectionUtils.addToList(standardFilters, filter));
		}
		return filter;
	}

	public boolean isShowingAnyPoi() {
		return !getSelectedPoiFilters().isEmpty();
	}

	public boolean isShowingAnyGeneralPoi() {
		return !getGeneralSelectedPoiFilters().isEmpty();
	}

	public void clearGeneralSelectedPoiFilters() {
		clearSelectedPoiFilters(true);
	}

	public void clearAllSelectedPoiFilters() {
		clearSelectedPoiFilters(false);
	}

	private void clearSelectedPoiFilters(boolean saveWiki) {
		Set<PoiUIFilter> selectedPoiFilters = new ArraySet<>();
		PoiUIFilter wiki = getTopWikiPoiFilter();
		if (saveWiki && isPoiFilterSelected(wiki)) {
			selectedPoiFilters.add(wiki);
		}
		setSelectedPoiFilters(selectedPoiFilters);
	}

	private void setSelectedPoiFilters(@NonNull Set<PoiUIFilter> filters) {
		if (useOverwrittenFilters) {
			overwrittenSelectedPoiFilters = filters;
		} else {
			selectedPoiFilters = filters;
			saveSelectedPoiFilters(selectedPoiFilters);
		}
	}

	public String getSelectedPoiFiltersName() {
		return getFiltersName(getSelectedPoiFilters());
	}

	public String getGeneralSelectedPoiFiltersName() {
		return getFiltersName(getGeneralSelectedPoiFilters());
	}

	public String getFiltersName(Set<PoiUIFilter> filters) {
		if (filters.isEmpty()) {
			return app.getResources().getString(R.string.shared_string_none);
		} else {
			List<String> names = new ArrayList<>();
			for (PoiUIFilter filter : filters) {
				names.add(filter.getName());
			}
			return android.text.TextUtils.join(", ", names);
		}
	}

	public boolean isPoiFiltersSelected(@NonNull Collection<PoiUIFilter> filters) {
		for (PoiUIFilter filter : filters) {
			if (!isPoiFilterSelected(filter)) {
				return false;
			}
		}
		return !filters.isEmpty();
	}

	public boolean isPoiFilterSelected(PoiUIFilter filter) {
		return filter!= null && isPoiFilterSelected(filter.filterId);
	}

	public boolean isPoiFilterSelected(String filterId) {
		for (PoiUIFilter filter : getSelectedPoiFilters()) {
			if (filter.filterId.equals(filterId)) {
				return true;
			}
		}
		return false;
	}

	public void loadSelectedPoiFilters() {
		// don't deal with not loaded poi types
		if (!app.getPoiTypes().isInit()) {
			return;
		}
		Set<PoiUIFilter> selectedPoiFilters = new TreeSet<>();
		Set<String> selectedFiltersIds = settings.getSelectedPoiFilters();
		for (String filterId : selectedFiltersIds) {
			PoiUIFilter filter = getFilterById(filterId);
			if (filter != null) {
				selectedPoiFilters.add(filter);
			}
		}
		PluginsHelper.onPrepareExtraTopPoiFilters(selectedPoiFilters);
		this.selectedPoiFilters = selectedPoiFilters;
	}

	@Nullable
	public Pair<Long, Map<String, List<String>>> getCacheByResourceName(String fileName) {
		Pair<Long, Map<String, List<String>>> cache = null;
		PoiFilterDbHelper helper = openDbHelper();
		if (helper != null) {
			SQLiteConnection readableDb = helper.getReadableDatabase();
			if (readableDb != null) {
				cache = helper.getCacheByResourceName(readableDb, fileName);
			}
			helper.close();
		}
		return cache;
	}

	public void updateCacheForResource(String fileName, long lastModified, Map<String, List<String>> categories) {
		PoiFilterDbHelper helper = openDbHelper();
		if (helper != null) {
			SQLiteConnection readableDb = helper.getReadableDatabase();
			if (readableDb != null) {
				helper.updateCacheForResource(readableDb, fileName, lastModified, categories);
			}
			helper.close();
		}
	}

	public void insertCacheForResource(String fileName, long lastModified, Map<String, List<String>> categories) {
		PoiFilterDbHelper helper = openDbHelper();
		if (helper != null) {
			SQLiteConnection readableDb = helper.getReadableDatabase();
			if (readableDb != null) {
				helper.insertCacheForResource(readableDb, fileName, lastModified, categories);
			}
			helper.close();
		}
	}

	private void saveSelectedPoiFilters(Set<PoiUIFilter> selectedPoiFilters) {
		Set<String> filters = new HashSet<>();
		for (PoiUIFilter filter : selectedPoiFilters) {
			filters.add(filter.filterId);
		}
		settings.setSelectedPoiFilters(filters);
	}

	public class PoiFilterDbHelper {

		private static final int TRUE_INT = 1;
		private static final int FALSE_INT = 0;

		public static final String DATABASE_NAME = "poi_filters";
		private static final int DATABASE_VERSION = 7;

		private static final String FILTER_NAME = "poi_filters";
		private static final String FILTER_COL_NAME = "name";
		private static final String FILTER_COL_ID = "id";
		private static final String FILTER_COL_FILTERBYNAME = "filterbyname";
		private static final String FILTER_COL_HISTORY = "history";
		private static final String FILTER_COL_DELETED = "deleted";

		private static final String FILTER_TABLE_CREATE = "CREATE TABLE " +
				FILTER_NAME + " (" +
				FILTER_COL_NAME + ", " +
				FILTER_COL_ID + ", " +
				FILTER_COL_FILTERBYNAME + ", " +
				FILTER_COL_HISTORY + ", " +
				FILTER_COL_DELETED + ");";

		private static final String CATEGORIES_NAME = "categories";
		private static final String CATEGORIES_FILTER_ID = "filter_id";
		private static final String CATEGORIES_COL_CATEGORY = "category";
		private static final String CATEGORIES_COL_SUBCATEGORY = "subcategory";

		private static final String CATEGORIES_TABLE_CREATE = "CREATE TABLE " +
				CATEGORIES_NAME + " (" +
				CATEGORIES_FILTER_ID + ", " +
				CATEGORIES_COL_CATEGORY + ", " +
				CATEGORIES_COL_SUBCATEGORY + ");";

		private static final String POI_TYPES_CACHE_NAME = "poi_types_cache";
		private static final String MAP_FILE_NAME = "map_name";
		private static final String MAP_FILE_DATE = "map_date";
		private static final String CACHED_POI_CATEGORIES = "cached_categories";

		private static final String POI_CACHE_TABLE_CREATE = "CREATE TABLE " +
				POI_TYPES_CACHE_NAME + " (" +
				MAP_FILE_NAME + ", " +
				MAP_FILE_DATE + ", " +
				CACHED_POI_CATEGORIES + ");";

		private static final String CATEGORY_KEY = "category";
		private static final String SUB_CATEGORIES_KEY = "sub_categories";

		private static final String FILTERS_LAST_MODIFIED_NAME = "poi_filters";

		private final OsmandApplication context;
		private SQLiteConnection conn;
		private final MapPoiTypes mapPoiTypes;

		PoiFilterDbHelper(MapPoiTypes mapPoiTypes, OsmandApplication context) {
			this.mapPoiTypes = mapPoiTypes;
			this.context = context;
		}

		public SQLiteConnection getWritableDatabase() {
			return openConnection(false);
		}

		public void close() {
			if (conn != null) {
				conn.close();
				conn = null;
			}
		}

		public SQLiteConnection getReadableDatabase() {
			return openConnection(true);
		}

		private SQLiteConnection openConnection(boolean readonly) {
			conn = context.getSQLiteAPI().getOrCreateDatabase(DATABASE_NAME, readonly);
			if (conn != null && conn.getVersion() < DATABASE_VERSION) {
				if (readonly) {
					conn.close();
					conn = context.getSQLiteAPI().getOrCreateDatabase(DATABASE_NAME, false);
				}
				if (conn != null) {
					int version = conn.getVersion();
					conn.setVersion(DATABASE_VERSION);
					if (version == 0) {
						onCreate(conn);
					} else {
						onUpgrade(conn, version, DATABASE_VERSION);
					}
				}
			}
			return conn;
		}

		public void onCreate(SQLiteConnection conn) {
			conn.execSQL(FILTER_TABLE_CREATE);
			conn.execSQL(CATEGORIES_TABLE_CREATE);
			conn.execSQL(POI_CACHE_TABLE_CREATE);
		}


		public void onUpgrade(SQLiteConnection conn, int oldVersion, int newVersion) {
			boolean upgraded = false;
			if (newVersion <= 5) {
				deleteOldFilters(conn);
				upgraded = true;
			}
			if (oldVersion < 6) {
				conn.execSQL("ALTER TABLE " + FILTER_NAME + " ADD " + FILTER_COL_HISTORY + " int DEFAULT " + FALSE_INT);
				conn.execSQL("ALTER TABLE " + FILTER_NAME + " ADD " + FILTER_COL_DELETED + " int DEFAULT " + FALSE_INT);
				upgraded = true;
			}
			if (oldVersion < 7) {
				conn.execSQL(POI_CACHE_TABLE_CREATE);
				upgraded = true;
			}
			if (upgraded) {
				updateLastModifiedTime();
			}
		}

		public long getLastModifiedTime() {
			long lastModifiedTime = BackupUtils.getLastModifiedTime(context, FILTERS_LAST_MODIFIED_NAME);
			if (lastModifiedTime == 0) {
				File dbFile = context.getDatabasePath(DATABASE_NAME);
				lastModifiedTime = dbFile.exists() ? dbFile.lastModified() : 0;
				BackupUtils.setLastModifiedTime(context, FILTERS_LAST_MODIFIED_NAME, lastModifiedTime);
			}
			return lastModifiedTime;
		}

		public void setLastModifiedTime(long lastModifiedTime) {
			BackupUtils.setLastModifiedTime(context, FILTERS_LAST_MODIFIED_NAME, lastModifiedTime);
		}

		private void updateLastModifiedTime() {
			BackupUtils.setLastModifiedTime(context, FILTERS_LAST_MODIFIED_NAME);
		}

		private void deleteOldFilters(SQLiteConnection conn) {
			if (conn != null) {
				for (String toDel : DEL) {
					deleteFilter(conn, "user_" + toDel);
				}
			}
		}

		void doDeletion() {
			SQLiteConnection conn = getWritableDatabase();
			if (conn != null) {
				String query = "SELECT " + FILTER_COL_ID + ", " + FILTER_COL_HISTORY + ", " + FILTER_COL_DELETED + " FROM " + FILTER_NAME;
				SQLiteCursor cursor = conn.rawQuery(query, null);
				if (cursor != null) {
					if (cursor.moveToFirst()) {
						do {
							if (cursor.getInt(1) == FALSE_INT && cursor.getInt(2) == TRUE_INT) {
								deleteFilter(conn, cursor.getString(0));
							}
						} while (cursor.moveToNext());
					}
					cursor.close();
				}
			}
		}

		void markHistory(String filterId, boolean history) {
			SQLiteConnection conn = getWritableDatabase();
			if (conn != null) {
				conn.execSQL("UPDATE " + FILTER_NAME + " SET " + FILTER_COL_HISTORY + " = ? WHERE " + FILTER_COL_ID + " = ?",
						new Object[]{history ? TRUE_INT : FALSE_INT, filterId});
				updateLastModifiedTime();
			}
		}

		void clearHistory() {
			SQLiteConnection conn = getWritableDatabase();
			if (conn != null) {
				conn.execSQL("UPDATE " + FILTER_NAME + " SET " + FILTER_COL_HISTORY + " = ?", new Object[]{FALSE_INT});
				updateLastModifiedTime();
			}
		}

		protected boolean addFilter(PoiUIFilter p, SQLiteConnection db, boolean addOnlyCategories, boolean forHistory) {
			if (db != null) {
				if (!addOnlyCategories) {
					p.setDeleted(forHistory);
					int value = forHistory ? TRUE_INT : FALSE_INT;
					db.execSQL("INSERT INTO " + FILTER_NAME + " VALUES (?, ?, ?, ?, ?)",
							new Object[]{p.getName(), p.getFilterId(), p.getFilterByName(), value, value});
				}
				Map<PoiCategory, LinkedHashSet<String>> types = p.getAcceptedTypes();
				SQLiteStatement insertCategories = db.compileStatement("INSERT INTO " + CATEGORIES_NAME + " VALUES (?, ?, ?)");
				for (Map.Entry<PoiCategory, LinkedHashSet<String>> entry : types.entrySet()) {
					PoiCategory a = entry.getKey();
					if (entry.getValue() == null) {
						insertCategories.bindString(1, p.getFilterId());
						insertCategories.bindString(2, a.getKeyName());
						insertCategories.bindNull(3);
						insertCategories.execute();
					} else {
						for (String s : entry.getValue()) {
							insertCategories.bindString(1, p.getFilterId());
							insertCategories.bindString(2, a.getKeyName());
							insertCategories.bindString(3, s);
							insertCategories.execute();
						}
					}
				}
				insertCategories.close();
				updateLastModifiedTime();
				return true;
			}
			return false;
		}

		protected List<PoiUIFilter> getFilters(SQLiteConnection conn, boolean includeDeleted) {
			ArrayList<PoiUIFilter> list = new ArrayList<>();
			if (conn != null) {
				SQLiteCursor query = conn.rawQuery("SELECT " + CATEGORIES_FILTER_ID + ", " + CATEGORIES_COL_CATEGORY + "," + CATEGORIES_COL_SUBCATEGORY + " FROM " +
						CATEGORIES_NAME, null);
				Map<String, Map<PoiCategory, LinkedHashSet<String>>> map = new LinkedHashMap<>();
				if (query != null && query.moveToFirst()) {
					do {
						String filterId = query.getString(0);
						if (!map.containsKey(filterId)) {
							map.put(filterId, new LinkedHashMap<PoiCategory, LinkedHashSet<String>>());
						}
						Map<PoiCategory, LinkedHashSet<String>> m = map.get(filterId);
						PoiCategory a = mapPoiTypes.getPoiCategoryByName(query.getString(1).toLowerCase(), false);
						String subCategory = query.getString(2);
						if (subCategory == null) {
							m.put(a, null);
						} else if (!mapPoiTypes.isTypeForbidden(subCategory)) {
							if (m.get(a) == null) {
								m.put(a, new LinkedHashSet<String>());
							}
							m.get(a).add(subCategory);
						}
					} while (query.moveToNext());
				}
				if (query != null) {
					query.close();
				}

				query = conn.rawQuery("SELECT " +
						FILTER_COL_ID + ", " +
						FILTER_COL_NAME + ", " +
						FILTER_COL_FILTERBYNAME + ", " +
						FILTER_COL_DELETED +
						" FROM " + FILTER_NAME, null);
				if (query != null && query.moveToFirst()) {
					do {
						String filterId = query.getString(0);
						boolean deleted = query.getInt(3) == TRUE_INT;
						if (map.containsKey(filterId) && (includeDeleted || !deleted)) {
							String filterName = query.getString(1);
							String translation = app.getPoiTypes().getPoiTranslation(filterName);
							if(translation != null){
								filterName = translation;
							}
							PoiUIFilter filter = new PoiUIFilter(filterName, filterId,
									map.get(filterId), app);
							filter.setSavedFilterByName(query.getString(2));
							filter.setDeleted(deleted);
							if (filter.getAcceptedTypesCount() > 0) {
								list.add(filter);
							}
						}
					} while (query.moveToNext());
				}
				if (query != null) {
					query.close();
				}
			}
			return list;
		}

		protected boolean editFilter(SQLiteConnection conn, PoiUIFilter filter) {
			if (conn != null) {
				conn.execSQL("DELETE FROM " + CATEGORIES_NAME + " WHERE " + CATEGORIES_FILTER_ID + " = ?",
						new Object[]{filter.getFilterId()});
				addFilter(filter, conn, true, false);
				updateName(conn, filter);
				updateLastModifiedTime();
				return true;
			}
			return false;
		}

		private void updateName(SQLiteConnection db, PoiUIFilter filter) {
			db.execSQL("UPDATE " + FILTER_NAME + " SET " + FILTER_COL_FILTERBYNAME + " = ?, " + FILTER_COL_NAME + " = ? " + " WHERE "
					+ FILTER_COL_ID + "= ?", new Object[]{filter.getFilterByName(), filter.getName(), filter.getFilterId()});
			updateLastModifiedTime();
		}

		protected boolean deleteFilter(SQLiteConnection db, PoiUIFilter p, boolean force) {
			if (db != null) {
				if (force) {
					deleteFilter(db, p.getFilterId());
				} else {
					db.execSQL("UPDATE " + FILTER_NAME + " SET " + FILTER_COL_DELETED + " = ? WHERE " + FILTER_COL_ID + " = ?",
							new Object[]{TRUE_INT, p.getFilterId()});
				}
				updateLastModifiedTime();
				return true;
			}
			return false;
		}

		private void deleteFilter(@NonNull SQLiteConnection db, String key) {
			db.execSQL("DELETE FROM " + FILTER_NAME + " WHERE " + FILTER_COL_ID + " = ?", new Object[]{key});
			db.execSQL("DELETE FROM " + CATEGORIES_NAME + " WHERE " + CATEGORIES_FILTER_ID + " = ?", new Object[]{key});
			updateLastModifiedTime();
		}

		@Nullable
		protected Pair<Long, Map<String, List<String>>> getCacheByResourceName(@NonNull SQLiteConnection db, String fileName) {
			Pair<Long, Map<String, List<String>>> cache = null;
			SQLiteCursor query = db.rawQuery("SELECT " +
					MAP_FILE_DATE + ", " +
					CACHED_POI_CATEGORIES +
					" FROM " +
					POI_TYPES_CACHE_NAME +
					" WHERE " + MAP_FILE_NAME + " = ?", new String[]{fileName});
			if (query != null && query.moveToFirst()) {
				long lastModified = query.getLong(0);
				Map<String, List<String>> categories = getCategories(query.getString(1));
				cache = new Pair<>(lastModified, categories);
			}
			if (query != null) {
				query.close();
			}
			db.close();
			return cache;
		}

		private Map<String, List<String>> getCategories(String json) {
			Map<String, List<String>> categories = new HashMap<>();
			try {
				JSONArray jsonArray = new JSONArray(json);
				for (int i = 0; i < jsonArray.length(); i++) {
					JSONObject jsonObject = jsonArray.getJSONObject(i);
					String category = jsonObject.optString(CATEGORY_KEY);
					List<String> subCategories = getSubCategories(jsonObject.optString(SUB_CATEGORIES_KEY));
					categories.put(category, subCategories);
				}
			} catch (JSONException e) {
				LOG.error("Error parsing categories: " + e);
			}
			return categories;
		}

		protected void updateCacheForResource(@NonNull SQLiteConnection db, String fileName, long lastModified, Map<String, List<String>> categories) {
			try {
				db.execSQL("UPDATE " + POI_TYPES_CACHE_NAME + " SET " +
								MAP_FILE_DATE + " = ?, " +
								CACHED_POI_CATEGORIES + " = ? " +
								"WHERE " + MAP_FILE_NAME + " = ?",
						new Object[]{lastModified, getCategoriesJson(categories), fileName});
			} catch (JSONException e) {
				LOG.error("Error converting category to json: " + e);
			}
		}

		protected void insertCacheForResource(@NonNull SQLiteConnection db, String fileName, long lastModified, Map<String, List<String>> categories) {
			try {
				db.execSQL("INSERT INTO " + POI_TYPES_CACHE_NAME + " VALUES(?,?,?)",
						new Object[]{fileName, lastModified, getCategoriesJson(categories)});
			} catch (JSONException e) {
				LOG.error("Error converting category to json: " + e);
			}
		}

		private String getCategoriesJson(Map<String, List<String>> categories) throws JSONException {
			JSONArray json = new JSONArray();
			for (Map.Entry<String, List<String>> entry : categories.entrySet()) {
				JSONObject jsonObject = new JSONObject();
				JSONArray subCategories = new JSONArray();
				for (String subCategory : entry.getValue()) {
					subCategories.put(subCategory);
				}
				jsonObject.put(CATEGORY_KEY, entry.getKey());
				jsonObject.put(SUB_CATEGORIES_KEY, subCategories);
				json.put(jsonObject);
			}
			return json.toString();
		}

		private List<String> getSubCategories(@NonNull String json) throws JSONException {
			List<String> subCategories = new ArrayList<>();
			JSONArray jsonArray = new JSONArray(json);
			for (int i = 0; i < jsonArray.length(); i++) {
				subCategories.add(jsonArray.optString(i));
			}
			return subCategories;
		}
	}
}
