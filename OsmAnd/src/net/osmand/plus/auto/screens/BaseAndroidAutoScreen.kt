package net.osmand.plus.auto.screens

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import net.osmand.data.LatLon
import net.osmand.data.QuadRect
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.auto.views.CarSurfaceView
import net.osmand.plus.views.Zoom
import net.osmand.search.core.SearchResult
import net.osmand.util.Algorithms

abstract class BaseAndroidAutoScreen(carContext: CarContext) : Screen(carContext),
	DefaultLifecycleObserver {

	protected var prevElevationAngle = 90f
	protected var prevRotationAngle = 0f
	protected var prevZoom: Zoom? = null
	protected var prevMapLinkedToLocation = false
	protected val ANIMATION_RETURN_FROM_PREVIEW_TIME = 1500

	protected val app: OsmandApplication
		get() {
			return carContext.applicationContext as OsmandApplication
		}
	protected var contentLimit: Int = 0
		private set
	var session = app.carNavigationSession

	init {
		initContentLimit()
	}

	private fun initContentLimit() {
		val manager = carContext.getCarService(
			ConstraintManager::class.java
		)
		contentLimit = DEFAULT_CONTENT_LIMIT.coerceAtMost(
			manager.getContentLimit(getConstraintLimitType())
		)
	}

	protected open fun getConstraintLimitType(): Int {
		return ConstraintManager.CONTENT_LIMIT_TYPE_LIST
	}

	protected fun openRoutePreview(
		settingsAction: Action,
		result: SearchResult
	) {
		screenManager.pushForResult(
			RoutePreviewScreen(carContext, settingsAction, result, true)
		) { obj: Any? ->
			obj?.let {
				startNavigation()
				finish()
			}
		}
	}

	protected open fun onSearchResultSelected(result: SearchResult) {
	}

	private fun startNavigation() {
		app.osmandMap.mapLayers.mapActionsHelper.startNavigation()
		val session = app.carNavigationSession
		session?.startNavigationScreen()
	}

	protected fun createSearchAction() = Action.Builder()
		.setIcon(
			CarIcon.Builder(
				IconCompat.createWithResource(
					carContext, R.drawable.ic_action_search_dark
				)
			).build()
		)
		.setOnClickListener { openSearch() }
		.build()

	private fun openSearch() {
		app.carNavigationSession?.let { navigationSession ->
			screenManager.pushForResult(
				SearchScreen(
					carContext,
					navigationSession.settingsAction
				)
			) { _: Any? -> }
		}
	}

	protected open fun adjustMapToRect(location: LatLon, mapRect: QuadRect) {
		app.mapViewTrackingUtilities.isMapLinkedToLocation = false
		Algorithms.extendRectToContainPoint(mapRect, location.longitude, location.latitude)
		app.carNavigationSession?.navigationCarSurface?.let { surfaceRenderer ->
			if (!mapRect.hasInitialState()) {
				val mapView = app.osmandMap.mapView
				val tb = mapView.rotatedTileBox
				tb.rotate = 0f;
				tb.mapDensity = 1.0; //CarSurfaceView.CAR_DENSITY_SCALE.toDouble(); - strangely it works
				val rtl = false; // panel is always on the left
				val leftPanel =  tb.pixWidth / 2; // assume panel takes half screen
				val tileBoxWidthPx = tb.pixWidth - leftPanel;
				mapView.fitRectToMap(tb, mapRect.left, mapRect.right, mapRect.top, mapRect.bottom,
					tileBoxWidthPx, 0, 0, 0, rtl, true)
				mapView.refreshMap()
			}
		}
	}

	protected fun recenterMap() {
		session?.navigationCarSurface?.handleRecenter()
	}

	override fun onStop(owner: LifecycleOwner) {
		if (prevMapLinkedToLocation != app.mapViewTrackingUtilities.isMapLinkedToLocation) {
			app.mapViewTrackingUtilities.isMapLinkedToLocation = prevMapLinkedToLocation
		}
		restoreMapState()
	}

	protected open fun restoreMapState() {
		val mapView = app.osmandMap.mapView
		val locationProvider = app.locationProvider
		val lastKnownLocation = locationProvider.lastKnownLocation
		mapView.animateToState(
			lastKnownLocation?.latitude ?: mapView.latitude,
			lastKnownLocation?.longitude ?: mapView.longitude,
			prevZoom ?: mapView.currentZoom,
			prevRotationAngle,
			prevElevationAngle,
			ANIMATION_RETURN_FROM_PREVIEW_TIME.toLong(),
			false)
	}

	override fun onStart(owner: LifecycleOwner) {
		val mapView = app.osmandMap.mapView
		prevMapLinkedToLocation = app.mapViewTrackingUtilities.isMapLinkedToLocation
		prevZoom = mapView.currentZoom
		prevRotationAngle = mapView.rotate
		prevElevationAngle = mapView.normalizeElevationAngle(mapView.elevationAngle)
	}

	companion object {
		private const val DEFAULT_CONTENT_LIMIT = 12
	}
}