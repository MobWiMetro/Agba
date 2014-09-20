package com.td.httpclient;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Observable;
import java.util.Observer;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import android.annotation.SuppressLint;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;

public class HttpClientHelper extends Observable {

	String host = "http://aotasoft.com";

	public HttpClientHelper(Observer observer) {
		addObserver(observer);
	}

	private class HttpTask extends AsyncTask<Object, Void, Object> {
		private static final int HTTP_REQUEST_INDEX = 0x0;
		private static final String TAG = "HttpTask";

		@Override
		protected Object doInBackground(Object... params) {
			HttpUriRequest httpUriRequest = (HttpUriRequest) params[HTTP_REQUEST_INDEX];
			HttpParams httpParameters = new BasicHttpParams();
			HttpConnectionParams.setConnectionTimeout(httpParameters, 6000);
			HttpClient httpClient = new DefaultHttpClient(httpParameters);
			httpClient.getParams().setParameter(CoreProtocolPNames.PROTOCOL_VERSION, HttpVersion.HTTP_1_1);
			Object result = null;
			try {
				String url = httpUriRequest.getURI().toString();
				String a1 = "aota", a2 = "soft";
				String a3 = a1 + a2;
				if (!url.contains(a3))
					return null;
				Log.d(TAG, "-- Request URL: " + url);
				
				HttpResponse response = httpClient.execute(httpUriRequest);
				final int statusCode = response.getStatusLine().getStatusCode();
				if (statusCode != HttpStatus.SC_OK) {
					Log.e(TAG, "HttpClient Executive Error: " + statusCode);
					return null;
				}
				HttpEntity entity = response.getEntity();
				if (entity != null) {
					try {
						result = DataJsonParser.getInstance().parse(
								entity.getContent());
					} finally {
						entity.consumeContent();
					}
				}
			} catch (ClientProtocolException e) {
				Log.e(TAG, e.toString());
			} catch (IOException e) {
				Log.e(TAG, e.toString());
			}
			return result;
		}

		@Override
		protected void onPostExecute(Object result) {
			super.onPostExecute(result);
			setChanged();
			notifyObservers(result);
		}
	}

	public void getAdId(String app) {
		HttpGet httpUriRequest = new HttpGet(host + "/api/admob.php?app=" + app);
		android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY);
		HttpTask httpTask = new HttpTask();
		if(Build.VERSION.SDK_INT>=11)
			httpTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, httpUriRequest);
		else
			httpTask.execute(httpUriRequest);
	}

	public void update(String app, String locale, int vcode, String vname) {
		HttpGet httpUriRequest = null;
		try {
			httpUriRequest = new HttpGet(host + "/api/update/?id=" + app
					+ "&local=" + URLEncoder.encode(locale, "utf-8")
					+ "&vcode=" + vcode + "&vname=" + vname);
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			httpUriRequest = new HttpGet(host + "/api/update/?id=" + app
					+ "&local=&vcode=" + vcode + "&vname=" + vname);
			e.printStackTrace();
		}
		HttpTask httpTask = new HttpTask();
		if(Build.VERSION.SDK_INT>=11)
			httpTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, httpUriRequest);
		else
			httpTask.execute(httpUriRequest);
	}
}
