package com.gemuvn.gba;

import android.content.Context;
import android.content.SharedPreferences;

public class PreferenceHelper {
	public static final String PEFERENCE_FILE = "preference_com.gemuvn.gba";
	static SharedPreferences settings;
	static SharedPreferences.Editor editor;
	
	//Field
	
	final static String AD_ID		    = "pref_ad_id";
	
	public static String getAdId(Context context) {
		settings = context.getSharedPreferences(PEFERENCE_FILE, 0);
		return settings.getString(AD_ID, Common.aid);
	}

	public static void setAdId(Context context, String val) {
		settings = context.getSharedPreferences(PEFERENCE_FILE, 0);
		editor = settings.edit();
		editor.putString(AD_ID, val);
		editor.commit();
	}
}
