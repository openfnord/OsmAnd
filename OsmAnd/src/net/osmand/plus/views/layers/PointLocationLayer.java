package net.osmand.plus.views.layers;

import static android.graphics.Paint.ANTI_ALIAS_FLAG;
import static android.graphics.Paint.FILTER_BITMAP_FLAG;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import net.osmand.core.android.MapRendererView;
import net.osmand.core.jni.FColorRGB;
import net.osmand.core.jni.MapMarker;
import net.osmand.core.jni.MapMarkerBuilder;
import net.osmand.core.jni.MapMarkersCollection;
import net.osmand.core.jni.PointI;
import net.osmand.core.jni.SWIGTYPE_p_sk_spT_SkImage_const_t;
import net.osmand.core.jni.SWIGTYPE_p_void;
import net.osmand.core.jni.SwigUtilities;
import net.osmand.core.jni.Utilities;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.utils.AndroidUtils;
import net.osmand.Location;
import net.osmand.PlatformUtil;
import net.osmand.data.LatLon;
import net.osmand.data.PointDescription;
import net.osmand.data.RotatedTileBox;
import net.osmand.plus.utils.ColorUtilities;
import net.osmand.plus.OsmAndLocationProvider;
import net.osmand.plus.R;
import net.osmand.plus.base.MapViewTrackingUtilities;
import net.osmand.plus.profiles.ProfileIconColors;
import net.osmand.plus.settings.backend.ApplicationMode;
import net.osmand.plus.views.OsmandMapTileView;
import net.osmand.plus.views.layers.ContextMenuLayer.IContextMenuProvider;
import net.osmand.plus.views.layers.base.OsmandMapLayer;

import org.apache.commons.logging.Log;

import java.nio.ByteBuffer;
import java.util.List;

public class PointLocationLayer extends OsmandMapLayer implements IContextMenuProvider {
	private static final Log LOG = PlatformUtil.getLog(PointLocationLayer.class);

	protected final static float BEARING_SPEED_THRESHOLD = 0.1f;
	protected final static int RADIUS = 7;

	private Paint headingPaint;
	private Paint bitmapPaint;
	private Paint area;
	private Paint aroundArea;

	private OsmandMapTileView view;

	private ApplicationMode appMode;
	private boolean carView = false;
	private float textScale = 1f;
	@ColorInt
	private int color;
	@ColorInt
	private final static int OUTDATED_COLOR = 0xff777777;
	private LayerDrawable navigationIcon;
	private int navigationIconId;
	private LayerDrawable locationIcon;
	private int locationIconId;
	private Bitmap headingIcon;
	private int headingIconId;
	private OsmAndLocationProvider locationProvider;
	private final MapViewTrackingUtilities mapViewTrackingUtilities;
	private boolean nm;
	private boolean locationOutdated;

	// location pin marker
	private static final int MARKER_ID_MY_LOCATION = 1;
	private static final int MARKER_ID_NAVIGATION = 2;
	private static final int MARKER_ID_OUTDATED_LOCATION = 3;
	private MapMarkersCollection markersCollection;
	private MapMarker myLocationMarker;
	private MapMarker navigationMarker;
	private MapMarker outdatedLocationMarker;
	private SWIGTYPE_p_void onSurfaceIconKey = null;
	private SWIGTYPE_p_void onSurfaceHeadingIconKey = null;
	private boolean dirty = true;

	private enum State {
		MarkerStateStay,
		MarkerStateMove,
		MarkerStateOutdatedLocation
	}
	private State currentMarkerState = State.MarkerStateOutdatedLocation;

	private void setMarkerState(State markerState) {
		if (currentMarkerState == markerState) {
			return;
		}
		currentMarkerState = markerState;
		updateMarkerState();
	}

	private byte[] getBitmapAsByteArray(@NonNull Bitmap bitmap) {
		int size = bitmap.getRowBytes() * bitmap.getHeight();
		ByteBuffer byteBuffer = ByteBuffer.allocate(size);
		bitmap.copyPixelsToBuffer(byteBuffer);
		return byteBuffer.array();
	}

	private MapMarker recreateMarker(MapMarker oldMarker, LayerDrawable icon, int id, int baseColor) {
		if (view == null) {
			return null;
		}

		final MapRendererView mapRenderer = view.getMapRenderer();
		if (mapRenderer == null) {
			return null;
		}

		boolean newCollection = false;
		if (markersCollection == null) {
			markersCollection = new MapMarkersCollection();
			newCollection = true;
		} else if (oldMarker != null) {
			oldMarker.setIsHidden(true);
			oldMarker.setIsAccuracyCircleVisible(false);
			markersCollection.removeMarker(oldMarker);
		}

		MapMarkerBuilder myLocMarkerBuilder = new MapMarkerBuilder();
		myLocMarkerBuilder.setMarkerId(id);
		myLocMarkerBuilder.setIsAccuracyCircleSupported(true);
		myLocMarkerBuilder.setAccuracyCircleBaseColor(new FColorRGB((baseColor >> 16 & 0xff)/255.0f,
				((baseColor >> 8) & 0xff)/255.0f,
				((baseColor) & 0xff)/255.0f));
		myLocMarkerBuilder.setPinIconVerticalAlignment(MapMarker.PinIconVerticalAlignment.CenterVertical);
		myLocMarkerBuilder.setPinIconHorisontalAlignment(MapMarker.PinIconHorisontalAlignment.CenterHorizontal);
		myLocMarkerBuilder.setIsHidden(true);

		Bitmap markerBitmap = AndroidUtils.createScaledBitmap(icon, icon.getIntrinsicWidth(), icon.getIntrinsicHeight());
		if (markerBitmap != null) {
			SWIGTYPE_p_sk_spT_SkImage_const_t swigImg = SwigUtilities.createSkImageARGB888With(
					markerBitmap.getWidth(), markerBitmap.getHeight(), getBitmapAsByteArray(markerBitmap));
			onSurfaceIconKey = SwigUtilities.getOnSurfaceIconKey(1);
			myLocMarkerBuilder.addOnMapSurfaceIcon(onSurfaceIconKey, swigImg);
		}

		if (id != MARKER_ID_OUTDATED_LOCATION) {
			Bitmap headingBitmap = getScaledBitmapWithTint(headingIconId, getTextScale(), baseColor);
			if (headingBitmap != null) {
				SWIGTYPE_p_sk_spT_SkImage_const_t swigImg = SwigUtilities.createSkImageARGB888With(
						headingBitmap.getWidth(), headingBitmap.getHeight(), getBitmapAsByteArray(headingBitmap));
				onSurfaceHeadingIconKey = SwigUtilities.getOnSurfaceIconKey(2);
				myLocMarkerBuilder.addOnMapSurfaceIcon(onSurfaceHeadingIconKey, swigImg);
			}
		}

		MapMarker marker = myLocMarkerBuilder.buildAndAddToCollection(markersCollection);
		if (newCollection) {
			mapRenderer.addSymbolsProvider(markersCollection);
		}
		return marker;
	}

	private void invalidateMarkers() {
		if (view != null && view.hasMapRenderer()) {
			myLocationMarker = recreateMarker(myLocationMarker, locationIcon, MARKER_ID_MY_LOCATION, color);
			navigationMarker = recreateMarker(navigationMarker, navigationIcon, MARKER_ID_NAVIGATION, color);
			outdatedLocationMarker = recreateMarker(outdatedLocationMarker, locationIcon, MARKER_ID_OUTDATED_LOCATION, OUTDATED_COLOR);
		}
	}

	private void hideMarkers() {
		if (navigationMarker == null || myLocationMarker == null || outdatedLocationMarker == null) {
			return;
		}

		navigationMarker.setIsHidden(true);
		navigationMarker.setIsAccuracyCircleVisible(false);
		myLocationMarker.setIsHidden(true);
		myLocationMarker.setIsAccuracyCircleVisible(false);
		outdatedLocationMarker.setIsHidden(true);
		outdatedLocationMarker.setIsAccuracyCircleVisible(false);
	}

	private void updateMarkerState() {
		if (navigationMarker == null || myLocationMarker == null || outdatedLocationMarker == null) {
			return;
		}

		switch (currentMarkerState) {
			case MarkerStateMove:
				navigationMarker.setIsHidden(false);
				navigationMarker.setIsAccuracyCircleVisible(false);
				myLocationMarker.setIsHidden(true);
				myLocationMarker.setIsAccuracyCircleVisible(false);
				outdatedLocationMarker.setIsHidden(true);
				outdatedLocationMarker.setIsAccuracyCircleVisible(false);
				break;
			case MarkerStateStay:
				navigationMarker.setIsHidden(true);
				navigationMarker.setIsAccuracyCircleVisible(false);
				myLocationMarker.setIsHidden(false);
				myLocationMarker.setIsAccuracyCircleVisible(false);
				outdatedLocationMarker.setIsHidden(true);
				outdatedLocationMarker.setIsAccuracyCircleVisible(false);
				break;
			case MarkerStateOutdatedLocation:
				navigationMarker.setIsHidden(true);
				navigationMarker.setIsAccuracyCircleVisible(false);
				myLocationMarker.setIsHidden(true);
				myLocationMarker.setIsAccuracyCircleVisible(false);
				outdatedLocationMarker.setIsHidden(false);
				outdatedLocationMarker.setIsAccuracyCircleVisible(false);
				break;
		}
	}

	private void updateMarkerLocation(@NonNull Location lastKnownLocation, boolean hasHeading) {
		MapMarker marker = null;
		SWIGTYPE_p_void bearingIconKey = null;
		SWIGTYPE_p_void headingIconKey = null;
		switch (currentMarkerState) {
			case MarkerStateMove:
				marker = navigationMarker;
				bearingIconKey = onSurfaceIconKey;
				headingIconKey = onSurfaceHeadingIconKey;
				break;
			case MarkerStateStay:
				marker = myLocationMarker;
				headingIconKey = onSurfaceHeadingIconKey;
				break;
			case MarkerStateOutdatedLocation:
				marker = outdatedLocationMarker;
				break;
		}

		if (marker != null) {
			final PointI target31 = Utilities.convertLatLonTo31(
					new net.osmand.core.jni.LatLon(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude()));
			marker.setPosition(target31);
			marker.setIsAccuracyCircleVisible(true);
			marker.setAccuracyCircleRadius(lastKnownLocation.getAccuracy());
			if (headingIconKey != null || hasHeading) {
				marker.setOnMapSurfaceIconDirection(headingIconKey, locationProvider.getHeading());
			}
			if (bearingIconKey != null) {
				marker.setOnMapSurfaceIconDirection(bearingIconKey, lastKnownLocation.getBearing() - 90.0f);
			}
			if (marker.isHidden()) {
				marker.setIsHidden(false);
			}
		}
	}

	public PointLocationLayer(@NonNull Context context) {
		super(context);
		this.mapViewTrackingUtilities = getApplication().getMapViewTrackingUtilities();
	}

	private void initUI() {
		headingPaint = new Paint(ANTI_ALIAS_FLAG | FILTER_BITMAP_FLAG);
		bitmapPaint = new Paint(ANTI_ALIAS_FLAG | FILTER_BITMAP_FLAG);
		area = new Paint();
		aroundArea = new Paint();
		aroundArea.setStyle(Style.STROKE);
		aroundArea.setStrokeWidth(1);
		aroundArea.setAntiAlias(true);
		locationProvider = view.getApplication().getLocationProvider();
		updateIcons(view.getSettings().getApplicationMode(), false, locationProvider.getLastKnownLocation() == null);
	}

	@Override
	public void setMapActivity(@Nullable MapActivity mapActivity) {
		super.setMapActivity(mapActivity);
		if (mapActivity != null) {
			//invalidateMarkers();
			dirty = true;
		} else {
			hideMarkers();
			//destroyLayer();
		}
	}

	@Override
	public void initLayer(@NonNull OsmandMapTileView view) {
		this.view = view;
		initUI();
		dirty = true;
	}

	private RectF getHeadingRect(int locationX, int locationY){
		int rad = (int) (view.getDensity() * 60);
		return new RectF(locationX - rad, locationY - rad, locationX + rad, locationY + rad);
	}

	@Override
	public void onPrepareBufferImage(Canvas canvas, RotatedTileBox tileBox, DrawSettings settings) {
		if (dirty) {
			invalidateMarkers();
			dirty = false;
		}
	}

	@Override
	public void onDraw(Canvas canvas, RotatedTileBox box, DrawSettings nightMode) {
		if (view == null || box.getZoom() < 3) {
			return;
		}
		// draw
		boolean nm = nightMode != null && nightMode.isNightMode();
		Location lastKnownLocation = locationProvider.getLastStaleKnownLocation();
		updateIcons(view.getSettings().getApplicationMode(), nm,
				view.getApplication().getLocationProvider().getLastKnownLocation() == null);
		if (lastKnownLocation == null) {
			return;
		}

		// rendering
		if (view.hasMapRenderer()) {
/////////////////////////////////////////////////GPU////////////////////////////////////////////////
			if (isLocationVisible(box, lastKnownLocation)) {
				if (!locationOutdated) {
					boolean isBearing = lastKnownLocation.hasBearing() && (lastKnownLocation.getBearing() != 0.0f)
							&& (!lastKnownLocation.hasSpeed() || lastKnownLocation.getSpeed() > BEARING_SPEED_THRESHOLD);
					if (isBearing) {  // navigation
						setMarkerState(State.MarkerStateMove);
					} else {  // location
						setMarkerState(State.MarkerStateStay);
					}
				} else {  // outdated location
					setMarkerState(State.MarkerStateOutdatedLocation);
				}
				boolean hasHeading = !locationOutdated && locationProvider.getHeading() != null && mapViewTrackingUtilities.isShowViewAngle();
				updateMarkerLocation(lastKnownLocation, hasHeading);
			}
		} else {
/////////////////////////////////////////////////CPU////////////////////////////////////////////////
			int locationX;
			int locationY;
			if (mapViewTrackingUtilities.isMapLinkedToLocation()
					&& !MapViewTrackingUtilities.isSmallSpeedForAnimation(lastKnownLocation)
					&& !mapViewTrackingUtilities.isMovingToMyLocation()) {
				locationX = box.getCenterPixelX();
				locationY = box.getCenterPixelY();
			} else {
				locationX = box.getPixXFromLonNoRot(lastKnownLocation.getLongitude());
				locationY = box.getPixYFromLatNoRot(lastKnownLocation.getLatitude());
			}

			final double dist = box.getDistance(0, box.getPixHeight() / 2, box.getPixWidth(), box.getPixHeight() / 2);
			int radius = (int) (((double) box.getPixWidth()) / dist * lastKnownLocation.getAccuracy());
			if (radius > RADIUS * box.getDensity()) {
				int allowedRad = Math.min(box.getPixWidth() / 2, box.getPixHeight() / 2);
				canvas.drawCircle(locationX, locationY, Math.min(radius, allowedRad), area);
				canvas.drawCircle(locationX, locationY, Math.min(radius, allowedRad), aroundArea);
			}
			// draw bearing/direction/location
			if (isLocationVisible(box, lastKnownLocation)) {
				Float heading = locationProvider.getHeading();
				if (!locationOutdated && heading != null && mapViewTrackingUtilities.isShowViewAngle()) {
					canvas.save();
					canvas.rotate(heading - 180, locationX, locationY);
					canvas.drawBitmap(headingIcon, locationX - headingIcon.getWidth() / 2,
							locationY - headingIcon.getHeight() / 2, headingPaint);
					canvas.restore();
				}
				// Issue 5538: Some devices return positives for hasBearing() at rest, hence add 0.0 check:
				boolean isBearing = lastKnownLocation.hasBearing() && (lastKnownLocation.getBearing() != 0.0)
						&& (!lastKnownLocation.hasSpeed() || lastKnownLocation.getSpeed() > 0.1);
				if (!locationOutdated && isBearing) {
					float bearing = lastKnownLocation.getBearing();
					canvas.rotate(bearing - 90, locationX, locationY);
					int width = (int) (navigationIcon.getIntrinsicWidth() * textScale);
					int height = (int) (navigationIcon.getIntrinsicHeight() * textScale);
					width += width % 2 == 1 ? 1 : 0;
					height += height % 2 == 1 ? 1 : 0;
					if (textScale == 1) {
						navigationIcon.setBounds(locationX - width / 2, locationY - height / 2,
								locationX + width / 2, locationY + height / 2);
						navigationIcon.draw(canvas);
					} else {
						navigationIcon.setBounds(0, 0, width, height);
						Bitmap bitmap = AndroidUtils.createScaledBitmap(navigationIcon, width, height);
						canvas.drawBitmap(bitmap, locationX - width / 2, locationY - height / 2, bitmapPaint);
					}
				} else {
					int width = (int) (locationIcon.getIntrinsicWidth() * textScale);
					int height = (int) (locationIcon.getIntrinsicHeight() * textScale);
					width += width % 2 == 1 ? 1 : 0;
					height += height % 2 == 1 ? 1 : 0;
					if (textScale == 1) {
						locationIcon.setBounds(locationX - width / 2, locationY - height / 2,
								locationX + width / 2, locationY + height / 2);
						locationIcon.draw(canvas);
					} else {
						locationIcon.setBounds(0, 0, width, height);
						Bitmap bitmap = AndroidUtils.createScaledBitmap(locationIcon, width, height);
						canvas.drawBitmap(bitmap, locationX - width / 2, locationY - height / 2, bitmapPaint);
					}
				}
			}
		}
	}

	private boolean isLocationVisible(RotatedTileBox tb, Location l) {
		return l != null && tb.containsLatLon(l.getLatitude(), l.getLongitude());
	}

	@Override
	public void destroyLayer() {
		if (view == null) {
			return;
		}

		final MapRendererView mapRenderer = view.getMapRenderer();
		if (mapRenderer != null) {
			mapRenderer.removeSymbolsProvider(markersCollection);
			markersCollection = null;
			myLocationMarker = null;
			navigationMarker = null;
			outdatedLocationMarker = null;
		}
	}

	protected Bitmap getScaledBitmapWithTint(@DrawableRes int drawableId, float scale, int tint) {
		Drawable drawableIcon = AppCompatResources.getDrawable(view.getContext(), drawableId);
		if (drawableIcon != null) {
			DrawableCompat.setTint(DrawableCompat.wrap(drawableIcon), tint);
		}
		Bitmap bitmap = AndroidUtils.drawableToBitmap(drawableIcon, true);
		if (bitmap != null && scale != 1f && scale > 0.0f) {
			bitmap = AndroidUtils.scaleBitmap(bitmap,
					(int) (bitmap.getWidth() * scale), (int) (bitmap.getHeight() * scale), false);
		}

		return bitmap;
	}

	private void updateIcons(ApplicationMode appMode, boolean nighMode, boolean locationOutdated) {
		Context ctx = view.getContext();
		int color = locationOutdated ?
				ContextCompat.getColor(ctx, ProfileIconColors.getOutdatedLocationColor(nighMode)) :
				appMode.getProfileColor(nighMode);
		int locationIconId = appMode.getLocationIcon().getIconId();
		int navigationIconId = appMode.getNavigationIcon().getIconId();
		int headingIconId = appMode.getLocationIcon().getHeadingIconId();
		float textScale = getTextScale();
		boolean carView = getApplication().getOsmandMap().getMapView().isCarView();
		if (appMode != this.appMode || this.nm != nighMode || this.locationOutdated != locationOutdated
				|| this.color != color
				|| this.locationIconId != locationIconId
				|| this.headingIconId != headingIconId
				|| this.navigationIconId != navigationIconId
				|| this.textScale != textScale
				|| this.carView != carView) {
			this.appMode = appMode;
			this.color = color;
			this.nm = nighMode;
			this.locationOutdated = locationOutdated;
			this.locationIconId = locationIconId;
			this.headingIconId = headingIconId;
			this.navigationIconId = navigationIconId;
			this.textScale = textScale;
			this.carView = carView;
			navigationIcon = (LayerDrawable) AppCompatResources.getDrawable(ctx, navigationIconId);
			if (navigationIcon != null) {
				DrawableCompat.setTint(navigationIcon.getDrawable(1), color);
			}
			headingIcon = getScaledBitmap(headingIconId);
			headingPaint.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
			locationIcon = (LayerDrawable) AppCompatResources.getDrawable(ctx, locationIconId);
			if (locationIcon != null) {
				DrawableCompat.setTint(DrawableCompat.wrap(locationIcon.getDrawable(1)), color);
			}
			area.setColor(ColorUtilities.getColorWithAlpha(color, 0.16f));
			aroundArea.setColor(color);

			invalidateMarkers();
		}
	}

	@Override
	public boolean drawInScreenPixels() {
		return false;
	}

	@Override
	public void collectObjectsFromPoint(PointF point, RotatedTileBox tileBox, List<Object> o, boolean unknownLocation) {
		if (tileBox.getZoom() >= 3) {
			getMyLocationFromPoint(tileBox, point, o);
		}
	}

	@Override
	public LatLon getObjectLocation(Object o) {
		return getMyLocation();
	}

	@Override
	public PointDescription getObjectName(Object o) {
		return new PointDescription(PointDescription.POINT_TYPE_MY_LOCATION,
				view.getContext().getString(R.string.shared_string_my_location), "");
	}

	@Override
	public boolean disableSingleTap() {
		return false;
	}

	@Override
	public boolean disableLongPressOnMap(PointF point, RotatedTileBox tileBox) {
		return false;
	}

	@Override
	public boolean isObjectClickable(Object o) {
		return false;
	}

	@Override
	public boolean runExclusiveAction(Object o, boolean unknownLocation) {
		return false;
	}

	@Override
	public boolean showMenuAction(@Nullable Object o) {
		return false;
	}

	private LatLon getMyLocation() {
		Location location = locationProvider.getLastKnownLocation();
		if (location != null) {
			return new LatLon(location.getLatitude(), location.getLongitude());
		} else {
			return null;
		}
	}

	private void getMyLocationFromPoint(RotatedTileBox tb, PointF point, List<? super LatLon> myLocation) {
		LatLon location = getMyLocation();
		if (location != null && view != null) {
			int ex = (int) point.x;
			int ey = (int) point.y;
			int x = (int) tb.getPixXFromLatLon(location.getLatitude(), location.getLongitude());
			int y = (int) tb.getPixYFromLatLon(location.getLatitude(), location.getLongitude());
			int rad = (int) (18 * tb.getDensity());
			if (Math.abs(x - ex) <= rad && (ey - y) <= rad && (y - ey) <= 2.5 * rad) {
				myLocation.add(location);
			}
		}
	}
}
