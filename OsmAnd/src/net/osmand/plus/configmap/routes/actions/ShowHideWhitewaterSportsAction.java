package net.osmand.plus.configmap.routes.actions;

import static net.osmand.plus.quickaction.QuickActionIds.SHOW_HIDE_WHITEWATER_SPORTS_ROUTES_ACTION;

import androidx.annotation.NonNull;

import net.osmand.osm.OsmRouteType;
import net.osmand.plus.R;
import net.osmand.plus.quickaction.QuickAction;
import net.osmand.plus.quickaction.QuickActionType;

public class ShowHideWhitewaterSportsAction extends BaseRouteQuickAction {

	public static final QuickActionType TYPE = new QuickActionType(SHOW_HIDE_WHITEWATER_SPORTS_ROUTES_ACTION,
			"whitewater_sports.routes.showhide", ShowHideWhitewaterSportsAction.class)
			.nameActionRes(R.string.quick_action_verb_show_hide)
			.nameRes(R.string.rendering_attr_whiteWaterSports_name)
			.iconRes(R.drawable.ic_action_kayak)
			.category(QuickActionType.CONFIGURE_MAP);

	public ShowHideWhitewaterSportsAction() {
		super(TYPE);
	}

	public ShowHideWhitewaterSportsAction(QuickAction quickAction) {
		super(quickAction);
	}

	@Override
	public QuickActionType getActionType() {
		return TYPE;
	}

	@NonNull
	@Override
	protected OsmRouteType getOsmRouteType() {
		return OsmRouteType.WATER;
	}
}
