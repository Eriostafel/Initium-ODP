package com.universeprojects.miniup.server.longoperations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.universeprojects.cacheddatastore.CachedEntity;
import com.universeprojects.cacheddatastore.QueryHelper;
import com.universeprojects.miniup.CommonChecks;
import com.universeprojects.miniup.server.GameUtils;
import com.universeprojects.miniup.server.InitiumObject;
import com.universeprojects.miniup.server.ODPDBAccess;
import com.universeprojects.miniup.server.aspects.AspectFireplace;
import com.universeprojects.miniup.server.commands.framework.UserErrorMessage;
import com.universeprojects.miniup.server.services.MainPageUpdateService;

public class LongOperationRest extends LongOperation {

	public LongOperationRest(ODPDBAccess db, 
			Map<String, String[]> requestParameters) throws UserErrorMessage {
		super(db, requestParameters);
		// TODO Auto-generated constructor stub
	}

	@Override
	int doBegin(Map<String, String> parameters) throws UserErrorMessage 
	{
		Key locationKey = (Key)db.getCurrentCharacter().getProperty("locationKey");
		CachedEntity location = db.getEntity(locationKey);
		
		String locationType = (String)location.getProperty("type");
		if (GameUtils.isPlayerIncapacitated(db.getCurrentCharacter()))
			throw new UserErrorMessage("You're incapacitated, you can't do this right now.");
		if ("RestSite".equals(locationType)==false && "CampSite".equals(locationType)==false)
			throw new UserErrorMessage("You cannot rest here. Find a rest site like a camp or an Inn, or even a player's house.");
		
		Double hitpointsToRegain = (Double)db.getCurrentCharacter().getProperty("maxHitpoints")-(Double)db.getCurrentCharacter().getProperty("hitpoints");
		if (hitpointsToRegain<=0)
			throw new UserErrorMessage("You don't need to rest, you're already at full health! NOW GET OUT THERE AND KICK SOME ASS!");
		
		// Check, if it's night time and we're outside, that we have a fire going
//		if (GameUtils.getDayNight()>0.9 && CommonChecks.checkLocationIsOutside(location))
//		{
//			QueryHelper query = new QueryHelper(ds);
//			List<CachedEntity> entities = query.getFilteredList("Item", 50, null, "containerKey", FilterOperator.EQUAL, locationKey, "_aspects", FilterOperator.EQUAL, "Fireplace");
//			List<InitiumObject> fireplaces = InitiumObject.wrap(db, entities);
//			long currentTime = System.currentTimeMillis();
//			boolean fireIsActive = false;
//			long bestMinutesRemaining = 0l;
//			for(InitiumObject fireplace:fireplaces)
//			{
//				AspectFireplace aspect = (AspectFireplace)fireplace.getInitiumAspect("Fireplace");
//				if (aspect.isFireActive(currentTime))
//				{
//					long minutesRemaining = aspect.getMinutesUntilExpired(currentTime);
//					if (minutesRemaining>bestMinutesRemaining)
//						bestMinutesRemaining = minutesRemaining;
//					fireIsActive = true;
//					break;
//				}
//			}
//			
//			if (bestMinutesRemaining<45)
//				throw new UserErrorMessage("The fire is not strong enough, add more fuel to it. You cannot rest at night unless there is an active fire going.");
//			
//			if (fireIsActive==false)
//				throw new UserErrorMessage("It's night time but there is no campfire. You cannot rest at night unless there is an active fire going.");
//		}
		
		
		setDataProperty("description", "It will take "+hitpointsToRegain.intValue()+" seconds to regain your health.");
		
		return hitpointsToRegain.intValue();
	}

	@Override
	String doComplete() throws UserErrorMessage {
		db.setValue_StringStringMap(db.getCurrentCharacter(), "combatStatsDamageMap", new HashMap<String,String>());
		db.doCharacterRestFully(db.getCurrentCharacter());
		
		Key locationKey = (Key)db.getCurrentCharacter().getProperty("locationKey");
		CachedEntity location = db.getEntity(locationKey);
		
		// If the character is resting in a "nice" location, then give the well rested buff
		// ie. If this is a player house, then it should be a RestSite and have an owner.
		if ("RestSite".equals(location.getProperty("type")) &&
				location.getProperty("ownerKey")!=null)
		{
			db.awardBuff_WellRested(ds, db.getCurrentCharacter());
		}
		else
		{
			// Check if there is an active campfire, if so we can award the well rested buff (temporarily until camps can be upgraded to forts at least and other things)
			QueryHelper query = new QueryHelper(ds);
			List<CachedEntity> entities = query.getFilteredList("Item", 50, null, "containerKey", FilterOperator.EQUAL, location.getKey(), "_aspects", FilterOperator.EQUAL, "Fireplace");
			List<InitiumObject> fireplaces = InitiumObject.wrap(db, entities);
			long currentTime = System.currentTimeMillis();
			long bestMinutesRemaining = 0l;
			for(InitiumObject fireplace:fireplaces)
			{
				AspectFireplace aspect = (AspectFireplace)fireplace.getInitiumAspect("Fireplace");
				if (aspect.isFireActive(currentTime))
				{
					long minutesRemaining = aspect.getMinutesUntilExpired(currentTime);
					if (minutesRemaining>bestMinutesRemaining)
						bestMinutesRemaining = minutesRemaining;
					break;
				}
			}
			
			if (bestMinutesRemaining>=45)
			{
				db.awardBuff_WellRested(ds, db.getCurrentCharacter());
			}
		}
		
		MainPageUpdateService mpus = new MainPageUpdateService(db, db.getCurrentUser(), db.getCurrentCharacter(), location, this);
		// Update character widget
		mpus.updateInBannerCharacterWidget();
		
		// Camp site might have been destroyed while resting, 
		// so update location banner, description, and monster count.
		mpus.updateLocationJs();
		mpus.updateLocationDescription();
		mpus.updateMonsterCountPanel();
		
		return "You are fully rested!";
	}


	@Override
	public String getPageRefreshJavascriptCall() {
		return "doRest()";
	}

	@Override
	public Map<String, Object> getStateData() {
		Map<String, Object> stateData = super.getStateData();
		
		stateData.put("description", getDataProperty("description"));
		
		return stateData;
	}


	
}
