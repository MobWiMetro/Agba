package com.td.httpclient;

import java.io.IOException;
import java.io.InputStream;

import org.json.JSONException;
import org.json.JSONTokener;

import android.util.Log;

public class DataJsonParser {
	private static final String TAG = "JsonParser";

	private volatile static DataJsonParser uniqueInstance;

	public static DataJsonParser getInstance() {
		if (uniqueInstance == null) {
			synchronized (HttpClientHelper.class) {
				if (uniqueInstance == null) {
					uniqueInstance = new DataJsonParser();
				}
			}
		}
		return uniqueInstance;
	}

	/*private static String convertStreamToString(InputStream is) {
		if (is == null)
			return null;
		BufferedReader reader = new BufferedReader(new InputStreamReader(is));
		StringBuilder sb = new StringBuilder();
		String line = null;
		try {
			while ((line = reader.readLine()) != null) {
				sb.append(line);
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				is.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return sb.toString();
	}*/

	public Object parse(InputStream in) {
		if (in == null) return null;
		try {
			//String strData = convertStreamToString(in);
			String strData = IOUtils.toString(in);
			if (strData == null) return null;
			return new JSONTokener(strData).nextValue();
		} catch (JSONException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
}
