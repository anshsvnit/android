package com.duckduckgo.mobile.android.db;

import java.util.ArrayList;
import java.util.Set;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;

import com.duckduckgo.mobile.android.objects.FeedObject;
import com.duckduckgo.mobile.android.objects.SavedResultObject;
import com.duckduckgo.mobile.android.objects.history.HistoryObject;
import com.duckduckgo.mobile.android.util.AppShortInfo;

public class DdgDB {

	private static final String DATABASE_NAME = "ddg.db";
	private static final int DATABASE_VERSION = 10;
	private static final String FEED_TABLE = "feed";
	private static final String APP_TABLE = "apps";
	private static final String HISTORY_TABLE = "history";
	private static final String SAVED_OTHERS_TABLE = "saved_others";
	
	
	private SQLiteDatabase db;

	private SQLiteStatement insertStmt, insertStmtApp;
	// private static final String INSERT = "insert or ignore into " + FEED_TABLE + " (_id,title,description,feed,url,imageurl,favicon,timestamp,category,type) values (?,?,?,?,?,?,?,?,?,?)";
	private static final String INSERT = "insert or replace into " + FEED_TABLE + " (_id,title,description,feed,url,imageurl,favicon,timestamp,category,type, hidden) values (?,?,?,?,?,?,?,?,?,?,?)";
	
	private static final String APP_INSERT = "insert or replace into " + APP_TABLE + " (title,package) values (?,?)";
	
	// if type = recent search, data = query.  if type = web page / feed item, data = title, url is target
	// extraType is for feed source
//	private static final String HISTORY_INSERT = "insert or replace into " + HISTORY_TABLE + " (type, data, url, extraType) values (?,?,?,?)";

	
	public DdgDB(Context context) {
	      OpenHelper openHelper = new OpenHelper(context);
	      this.db = openHelper.getWritableDatabase();
	      this.insertStmt = this.db.compileStatement(INSERT);
	      this.insertStmtApp = this.db.compileStatement(APP_INSERT);
	}
	
	public long insertSavedResult(SavedResultObject object) {
		ContentValues contentValues = new ContentValues();
		contentValues.put("title", object.getTitle());
		contentValues.put("url", object.getUrl());
		contentValues.put("imageUrl", object.getImageUrl());
		// delete old record if exists
		this.db.delete(SAVED_OTHERS_TABLE, "title=? AND url=?", new String[]{object.getTitle(), object.getUrl()});
		return this.db.insert(SAVED_OTHERS_TABLE, null, contentValues);
	}
	
	/**
	 * insert a FeedObject to SQLite database
	 * for feed items, the existing FeedObject is saved.
	 * for ordinary webpages (including SERP), (url, title) pair is received here.
	 * 
	 * if title == null (happens often e.g. pages only containing an image), URL is used for title field.
	 *   
	 * 
	 * @param e
	 * @return if FeedObject(url,title) both null, return -1. Return Insert execution result otherwise
	 */
	public long insert(FeedObject e, String hidden) {
		String title = e.getTitle();		
		String url = e.getUrl();
		if(url == null || url.length() == 0)
			return -1l;
		
		if(title == null) {
			title = e.getUrl();
		}
	      this.insertStmt.bindString(1, e.getId());
	      this.insertStmt.bindString(2, title);
	      this.insertStmt.bindString(3, e.getDescription());
	      this.insertStmt.bindString(4, e.getFeed());
	      this.insertStmt.bindString(5, e.getUrl());
	      this.insertStmt.bindString(6, e.getImageUrl());
	      this.insertStmt.bindString(7, e.getFavicon());
	      this.insertStmt.bindString(8, e.getTimestamp());
	      this.insertStmt.bindString(9, e.getCategory());
	      this.insertStmt.bindString(10, e.getType());
	      this.insertStmt.bindString(11, hidden);
	      long result = this.insertStmt.executeInsert();
	      return result;
	}
	
	/**
	 * Ordinary item Save operation - keep Saved item VISIBLE  
	 * @param e
	 * @return
	 */
	public long insert(FeedObject e) {
		// hidden = False, F
		return this.insert(e, "F");
	}
	
	/**
	 * default item Save for browsed feed items - HIDDEN
	 * when Save is used this will become VISIBLE 
	 * @param e
	 * @return
	 */
	public long insertHidden(FeedObject e) {
		// hidden = True, T
		return this.insert(e, "T");
	}
	
	public long insertApp(AppShortInfo appInfo) {
	      this.insertStmtApp.bindString(1, appInfo.name);
	      this.insertStmtApp.bindString(2, appInfo.packageName);
	      long result = this.insertStmtApp.executeInsert();
	      return result;
	}
	
	public long insertRecentSearch(String query) {
		ContentValues contentValues = new ContentValues();
		contentValues.put("type", "R");
		contentValues.put("data", query);
		contentValues.put("url", "");
		contentValues.put("extraType", "");
		contentValues.put("feedId", "");
		// delete old record if exists
		this.db.delete(HISTORY_TABLE, "type='R' AND data=?", new String[]{query});
		return this.db.insert(HISTORY_TABLE, null, contentValues);
	}
	
	public long insertHistoryObject(HistoryObject object) {
		if(object.getType().equals("F")) {
			return insertFeedItem(object.getData(), object.getUrl(), object.getExtraType(), object.getFeedId());
		}
		else if(object.getType().equals("W")) {
			return insertWebPage(object.getData(), object.getUrl());
		}
		else if(object.getType().equals("R")) {
			return insertRecentSearch(object.getData());
		}
		return -1L;
	}
	
	public long insertWebPage(String title, String url) {
		ContentValues contentValues = new ContentValues();
		contentValues.put("type", "W");
		contentValues.put("data", title);
		contentValues.put("url", url);
		contentValues.put("extraType", "");
		contentValues.put("feedId", "");
		// delete old record if exists
		this.db.delete(HISTORY_TABLE, "type='W' AND data=? AND url=?", new String[]{title, url});
		return this.db.insert(HISTORY_TABLE, null, contentValues);
	}
	
	public long insertFeedItem(String title, String url, String extraType, String feedId) {		
        ContentValues contentValues = new ContentValues();
        contentValues.put("type", "F");
        contentValues.put("data", title);
        contentValues.put("url", url);
        contentValues.put("extraType", extraType);
        contentValues.put("feedId", feedId);
        // delete old record if exists
     	this.db.delete(HISTORY_TABLE, "type='F' AND feedId=?", new String[]{feedId});
        long res = this.db.insertOrThrow(HISTORY_TABLE, null, contentValues);        
        return res;
	}
	
	public long insertFeedItem(FeedObject feedObject) {
		this.insertHidden(feedObject);
		
		String feedId = feedObject.getId();		
        ContentValues contentValues = new ContentValues();
        contentValues.put("type", "F");
        contentValues.put("data", feedObject.getTitle());
        contentValues.put("url", feedObject.getUrl());
        contentValues.put("extraType", feedObject.getType());
        contentValues.put("feedId", feedId);
        // delete old record if exists
     	this.db.delete(HISTORY_TABLE, "type='F' AND feedId=?", new String[]{feedId});
        long res = this.db.insertOrThrow(HISTORY_TABLE, null, contentValues);        
        return res;
	}
	
	/**
	 * make a hidden feed item VISIBLE
	 * @param feedObject
	 * @return
	 */
	public long makeItemVisible(String id) {
	      ContentValues args = new ContentValues();
	      args.put("hidden", "F");
		return this.db.update(FEED_TABLE, args, "_id=?", new String[]{id});
	}
	
	/**
	 * make a hidden feed item VISIBLE
	 * @param feedObject
	 * @return
	 */
	public long makeItemHidden(String id) {
	      ContentValues args = new ContentValues();
	      args.put("hidden", "T");
		return this.db.update(FEED_TABLE, args, "_id=?", new String[]{id});
	}
	
	public void deleteApps() {
	      this.db.delete(APP_TABLE, null, null);
	}
	
	public void deleteHistory() {
	      this.db.delete(HISTORY_TABLE, null, null);
	}
	
//	public long update(FeedObject e) {
//	      ContentValues args = new ContentValues();
//	      args.put("text", e.text);
//	      return this.db.update(FEED_TABLE, args, "id=" + String.valueOf(e.id), null);
//	}

	public void deleteAll() {
	      this.db.delete(FEED_TABLE, null, null);
	}
	
	public int deleteFeedObject(FeedObject object) {
		  return this.db.delete(FEED_TABLE, "_id=?", new String[]{object.getId()});
	}
	
	public int deleteOthersByDataUrl(String data, String url) {
		  return this.db.delete(SAVED_OTHERS_TABLE, "title=? AND url=?", new String[]{data, url});
	}
	
	public int deleteHistoryByDataUrl(String data, String url) {
	      return this.db.delete(HISTORY_TABLE, "data=? AND url=?", new String[]{data, url});
	}
	
	public int deleteHistoryByFeedId(String feedId) {
	      return this.db.delete(HISTORY_TABLE, "feedId=?", new String[]{feedId});
	}
	
	private FeedObject getFeedObject(Cursor c) {
		return new FeedObject(c.getString(0), c.getString(1), c.getString(2), c.getString(3),
				c.getString(4), c.getString(5), c.getString(6), c.getString(7), c.getString(8), c.getString(9));
	}
	
	private AppShortInfo getAppShortInfo(Cursor c) {
		return new AppShortInfo(c.getString(0), c.getString(1));
	}
	
	private HistoryObject getHistoryObject(Cursor c) {
		return new HistoryObject(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4));
	}
	
	public ArrayList<AppShortInfo> selectApps(String title){
		Cursor c = this.db.query(APP_TABLE, null, "title MATCH ?", new String[]{title+"*"} , null, null, null);
		if(c.moveToFirst()) {
			ArrayList<AppShortInfo> apps = new ArrayList<AppShortInfo>(20);
			do {
				apps.add(getAppShortInfo(c));
			} while(c.moveToNext());

			return apps;
		}
		
		return null;
	}
	
	public ArrayList<FeedObject> selectAll(){
		Cursor c = this.db.query(FEED_TABLE, null, null, null , null, null, null);
		if(c.moveToFirst()) {
			ArrayList<FeedObject> feeds = new ArrayList<FeedObject>(30);
			do {
				feeds.add(getFeedObject(c));
			} while(c.moveToNext());

			return feeds;
		}
		
		return null;
	}
	
	/**
	 * for checking feed items
	 * @param id
	 * @return
	 */
	public boolean isSaved(String id) {
		Cursor c = this.db.query(FEED_TABLE, null, "_id=? AND hidden='F'", new String[]{id} , null, null, null);
		return c.moveToFirst();
	}
	
	/**
	 * for checking saved results
	 * @param id
	 * @return
	 */
	public boolean isSavedInOthersById(String id) {
		Cursor c = this.db.query(SAVED_OTHERS_TABLE, null, "_id=?", new String[]{id} , null, null, null);
		return c.moveToFirst();
	}
	
	/**
	 * for checking saved results
	 * @param id
	 * @return
	 */
	public boolean isSavedInOthers(String title, String url) {
		Cursor c = this.db.query(SAVED_OTHERS_TABLE, null, "title=? AND url=?", new String[]{title, url} , null, null, null);
		return c.moveToFirst();
	}
	
	/**
	 * for checking ordinary web pages
	 * @param pageTitle
	 * @param pageUrl
	 * @return
	 */
	public boolean isSaved(String pageTitle, String pageUrl) {
		if(pageUrl == null)
			return false;
		
		if(pageTitle == null)
			pageTitle = "";
			
		Cursor c = this.db.query(FEED_TABLE, null, "title=? AND url=? AND hidden='F'", new String[]{pageTitle, pageUrl} , null, null, null);
		return c.moveToFirst();
	}
	
	/**
	 * for checking ordinary web pages
	 * @param pageTitle
	 * @param pageUrl
	 * @return
	 */
	public boolean isSavedInHistory(String data, String url) {
		if(url == null)
			return false;
		
		if(data == null)
			data = "";
			
		Cursor c = this.db.query(HISTORY_TABLE, null, "data=? AND url=?", new String[]{data, url} , null, null, null);
		return c.moveToFirst();
	}
	
	public FeedObject selectById(String id){
		Cursor c = this.db.query(FEED_TABLE, null, "_id=? AND hidden='F'", new String[]{id} , null, null, null);
		if(c.moveToFirst()) {
			return getFeedObject(c);
		}
		return null;
	}
	
	public FeedObject selectHiddenById(String id){
		Cursor c = this.db.query(FEED_TABLE, null, "_id=? AND hidden='T'", new String[]{id} , null, null, null);
		if(c.moveToFirst()) {
			return getFeedObject(c);
		}
		return null;
	}
	
	public FeedObject selectByIdType(String id, String type){
		Cursor c = this.db.query(FEED_TABLE, null, "_id=? AND type = ? AND hidden='F'", new String[]{id,type} , null, null, null);
		if(c.moveToFirst()) {
			return getFeedObject(c);
		}
		return null;
	}
	
	public ArrayList<FeedObject> selectByType(String type){
		if(type == null) {
			return null;
		}
		
		ArrayList<FeedObject> feeds = new ArrayList<FeedObject>(20);
		Cursor c = this.db.query(FEED_TABLE, null, "type = ? AND hidden='F'", new String[]{type}, null, null, null, null);
		if (c.moveToFirst()) {
			do {
				FeedObject e = getFeedObject(c);
				feeds.add(e);
			} while (c.moveToNext());
		}
		if (c != null && !c.isClosed()) {
			c.close();
		}
		
		if(feeds.isEmpty()){
			return null;
		}

		return feeds;
	}
	
	public ArrayList<FeedObject> selectByType(Set<String> types){
		if(types == null || types.isEmpty()) {
			return null;
		}
		
		ArrayList<FeedObject> feeds = new ArrayList<FeedObject>(20);
		
		String query = "";
		if(types.size() > 1) {
			for(int i=0;i<types.size()-1;i++) {
				query += "type = ? OR ";
			}
		}
		query += "type = ?";
		
		String[] typeArray = (String[]) types.toArray(new String[types.size()]);
		
		Cursor c = this.db.query(FEED_TABLE, null, query, typeArray, null, null, null, null);
		if (c.moveToFirst()) {
			do {
				FeedObject e = getFeedObject(c);
				feeds.add(e);
			} while (c.moveToNext());
		}
		if (c != null && !c.isClosed()) {
			c.close();
		}
		
		if(feeds.isEmpty()){
			return null;
		}

		return feeds;
	}
	
	public ArrayList<HistoryObject> selectHistory(){
		Cursor c = this.db.query(HISTORY_TABLE, null, null, null , null, null, null);
		if(c.moveToFirst()) {
			ArrayList<HistoryObject> historyItems = new ArrayList<HistoryObject>(30);
			do {
				historyItems.add(getHistoryObject(c));
			} while(c.moveToNext());

			return historyItems;
		}
		
		return null;
	}
	
	public Cursor getCursorHistory() {
		return this.db.query(HISTORY_TABLE, null, null, null , null, null, "_id DESC");
	}
	
	public Cursor getCursorResultFeed() {
		return this.db.query(SAVED_OTHERS_TABLE, null, null, null , null, null, null);
	}
	
	public Cursor getCursorStoryFeed() {
		return this.db.query(FEED_TABLE, null, "NOT feed='' AND hidden='F'", null , null, null, null);
	}

	
	
	private static class OpenHelper extends SQLiteOpenHelper {

	      OpenHelper(Context context) {
	         super(context, DATABASE_NAME, null, DATABASE_VERSION);
	      }

		    @Override
		  	public void onCreate(SQLiteDatabase db) {
		  			  
		  			  db.execSQL("CREATE TABLE " + FEED_TABLE + "(" 
		  			    +"_id VARCHAR(300) UNIQUE, "
		  			    +"title VARCHAR(300), "
		  			    +"description VARCHAR(300), "
		  			    +"feed VARCHAR(300), "
		  			    +"url VARCHAR(300), "
		  			    +"imageurl VARCHAR(300), "
		  			    +"favicon VARCHAR(300), "
		  			    +"timestamp VARCHAR(300), "
		  			    +"category VARCHAR(300), "
		  			    +"type VARCHAR(300), "
		  			    +"hidden CHAR(1)"
		  			    +")"
		  			    );
		  			  
		  			  db.execSQL("CREATE INDEX idx_id ON " + FEED_TABLE + " (_id) ");
		  			  db.execSQL("CREATE INDEX idx_idtype ON " + FEED_TABLE + " (_id, type) ");
		  			  
		  			db.execSQL("CREATE VIRTUAL TABLE " + APP_TABLE + " USING FTS3 (" 
			  			    +"title VARCHAR(300), "
			  			    +"package VARCHAR(300) "
			  			    +")"
			  			    );
		  			
		  			db.execSQL("CREATE TABLE " + HISTORY_TABLE + "("
		  					+"_id INTEGER PRIMARY KEY, "
			  			    +"type VARCHAR(300), "
			  			    +"data VARCHAR(300), "
			  			    +"url VARCHAR(300), "
			  			    +"extraType VARCHAR(300), "
			  			    +"feedId VARCHAR(300)"
			  			    +")"
			  			    );
		  			
		  			  db.execSQL("CREATE TABLE " + SAVED_OTHERS_TABLE + "(" 
				  			    +"_id INTEGER PRIMARY KEY, "
				  			    +"title VARCHAR(300), "
				  			    +"url VARCHAR(300), "
				  			    +"imageurl VARCHAR(300) "
				  			    +")"
				  			    );
		  			  
		  			db.execSQL("CREATE INDEX idx_titleurl ON " + SAVED_OTHERS_TABLE + " (title, url) ");

		  	}
	
		  	@Override
		  	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		  		db.execSQL("DROP TABLE IF EXISTS " + FEED_TABLE);
		  		db.execSQL("DROP TABLE IF EXISTS " + APP_TABLE);
		  		db.execSQL("DROP TABLE IF EXISTS " + HISTORY_TABLE);
		  		db.execSQL("DROP TABLE IF EXISTS " + SAVED_OTHERS_TABLE);
		  		onCreate(db);
		  	}
	}
	
	public void close(){
		db.close();
	}
	
	public SQLiteDatabase getSQLiteDB() {
		return db;
	}

}
