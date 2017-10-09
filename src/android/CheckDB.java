/*
       Licensed to the Apache Software Foundation (ASF) under one
       or more contributor license agreements.  See the NOTICE file
       distributed with this work for additional information
       regarding copyright ownership.  The ASF licenses this file
       to you under the Apache License, Version 2.0 (the
       "License"); you may not use this file except in compliance
       with the License.  You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing,
       software distributed under the License is distributed on an
       "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
       KIND, either express or implied.  See the License for the
       specific language governing permissions and limitations
       under the License.
*/

package com.mrboss.checkdb;

import org.json.JSONArray;
import org.json.JSONException;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Context;

import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

import android.os.Bundle;
import org.apache.cordova.*;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.apache.cordova.LOG;
import android.content.Intent;
import android.provider.Settings;

import android.media.*;

public class CheckDB extends CordovaPlugin {
	private CheckDB checkdb = this;
	private Context context;       //app对象
	private Context basecontext;  //重启目标对象
	private boolean isRunning = true;  //运行监听数据库线程条件
    private long dbTime;  //原数据库的最后一次修改时间
    private long bpTime;  //备份数据库的最后一次修改时间
    private ChainShopDao dao;  //操作数据库工具对象
	
    private int dbBackupCount;  //备份数据库中表的数量
    private int dbCount;  //当前数据库中表的数量
	//private int count;    //临时记录数据库表的数量,缓存作用
    SharedPreferences sps;  //记录备份数据库状态的偏好设置工具对象
	
	private File dbFile;  //源数据库文件
	private DBOpenHelper sdbHelper = null;  //获取目标操作数据库对象
	private long modifedTime;  //当前数据库最后一次修改时间
	private int tables;  //当前数据库中表的数量
	

  @Override  //本插件的执行入口
  public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
    context = cordova.getActivity().getApplicationContext();
	basecontext = cordova.getActivity().getBaseContext();
	
	if ("CheckDB_Action".equals(action)) {
      try {
		  
          sps = context.getSharedPreferences("tablesCount", Context.MODE_PRIVATE);
	      dao = new ChainShopDao(context);
		  
		  CheckThread ct = new CheckThread();
		  ct.start();
      } catch (Exception e) {
        return false;
      }
      callbackContext.success();
      return true;
    }
    return false;
  }
  
	/**
     * 监听数据库状态线程
     */
    class CheckThread extends Thread{
        @Override
        public void run() {
            while (isRunning){
                try {
                    //Log.d("tag", "开启数据库状态监听线程");
                    sleep(15*1000);

                    checkDB();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
	
	/**
     * 检查数据库情况
     */
    private void checkDB(){
        //File file = new File();
        dbBackupCount = sps.getInt("dbTableCount", 2);
        dbCount = dao.calcTable();
		if(dbCount == 0){
			return;  //获取数据库操作对象失败，结束本次操作
		}
        //Log.d("tag", "db=" + dbCount + ", bp=" + dbBackupCount);
        dbFile = context.getDatabasePath("/data/data/com.mrboss.offlineposapp/databases/ChainShop_Pos_5_0.db");
        dbTime = (dbFile.lastModified() / 1000);
        bpTime = sps.getLong("dbModifedTime", 1000);
        //Log.d("tag", "dbTime=" + dbTime + ", bpTime=" + bpTime);
        if(dbTime != bpTime){
            if (dbCount < dbBackupCount){
                //执行还原操作
                new BackupTask(context, dao).execute("restroeDatabase");
            }else {
                //执行备份操作
                new BackupTask(context, dao).execute("backupDatabase");
            }
        }

    }

	/**
	* 异步备份还原数据库任务类
	*/
	class BackupTask extends AsyncTask<String, Void, Integer> {
		private static final String COMMAND_BACKUP = "backupDatabase";  //备份
		private static final String COMMAND_RESTORE = "restroeDatabase";  //恢复
		private Context mContext;
		private ChainShopDao dao = null;
	
		public BackupTask(Context context, ChainShopDao dao) {
			this.dao = dao;
			this.mContext = context;
		}
	
		@Override
		protected Integer doInBackground(String... params) {
			// 获得正在使用的数据库路径，sdcard 目录下的 /dlion/db_dlion.db
			// 默认路径是 /data/data/(包名)/databases/*.db
			/*File dbFile = mContext.getDatabasePath(Environment
					.getExternalStorageDirectory().getAbsolutePath()
					+ "/dlion/db_dlion.db");*/
			dbFile = mContext.getDatabasePath("/data/data/com.mrboss.offlineposapp/databases/ChainShop_Pos_5_0.db");
			File exportDir = new File(Environment.getExternalStorageDirectory(), "BackupDB");
			if (!exportDir.exists()) {
				exportDir.mkdirs();
			}
			File backup = new File(exportDir, dbFile.getName());
			String command = params[0];
			if (command.equals(COMMAND_BACKUP)) {
				try {
					backup.createNewFile();
					fileCopy(dbFile, backup);
					// TODO: 2017/8/24 handler
					//保存当前备份数据库状态信息
					saveDBStateInfo(dbFile);
					return Log.d("backup", "备份ok");  //备份成功
				} catch (Exception e) {
					e.printStackTrace();
					return Log.d("backup", "备份fail");  //备份失败
				}
			} else if (command.equals(COMMAND_RESTORE)) {
				try {
					fileCopy(backup, dbFile);
					// TODO: 2017/8/24 handler
					//保存当前还原数据库状态信息
					saveDBStateInfo(dbFile);
					return Log.d("restore", "还原success");  //还原成功
				} catch (Exception e) {
					e.printStackTrace();
					return Log.d("restore", "还原fail");  //还原失败
				}finally{
					isRunning = false;  //关闭数据库监听线程
					//Intent to restart application
					Intent intent = basecontext.getPackageManager().getLaunchIntentForPackage(basecontext.getPackageName());
					intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
					cordova.getActivity().finish();
					checkdb.cordova.startActivityForResult((CordovaPlugin) checkdb, intent, 0);  //获取CheckDB自身对象(this)
				}
			} else {
				return null;
			}
		}
	
		/**
		* 复制文件
		* @param dbFile
		* @param backup
		* @throws IOException
		*/
		private void fileCopy(File dbFile, File backup) throws IOException {
			FileChannel inChannel = new FileInputStream(dbFile).getChannel();
			FileChannel outChannel = new FileOutputStream(backup).getChannel();
			try {
				inChannel.transferTo(0, inChannel.size(), outChannel);
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				if (inChannel != null) {
					inChannel.close();
				}
				if (outChannel != null) {
					outChannel.close();
				}
			}
		}
	
		/**
		* 保存数据库状态信息
		* @param dbFile
		*/
		private void saveDBStateInfo(File dbFile){
			modifedTime = (dbFile.lastModified() / 1000);
			tables = dao.calcTable();
			if(tables == 0){
				return;
			}
			SharedPreferences sps = mContext.getSharedPreferences("tablesCount", Context.MODE_PRIVATE);
			SharedPreferences.Editor editor = sps.edit();
			editor.putLong("dbModifedTime", modifedTime);
			editor.putInt("dbTableCount", tables);
			editor.commit();
			//Log.d("tag", tables + "写入记录信息成功");  //测试用
		}
	
	}
  
	/**
	* 操作数据库工具类
	*/
	class ChainShopDao {
		private Context context;
		public ChainShopDao(Context context) {
			this.context = context;
		}
		int count = 0;
		
		/**
		* 统计数据库中表的数量
		* @return
		*/
		public int calcTable(){
			if(sdbHelper == null){
				try {
					sdbHelper = new DBOpenHelper(context);
					//sdbHelper = null;  //测试用
					count = calc(sdbHelper);
					//Log.d("正常输出", "count=" + count);  //测试用
					return count;
				}catch (Exception e){
					//由于有两个操作数据库对象,防止崩溃
					//Log.d("获取数据库对象失败", "count=" + count);  //测试用
					return count;
				}
			}else{
				count = calc(sdbHelper);
				return count;
			}
		}
		
		public int calc(DBOpenHelper sdbHelper){
			SQLiteDatabase sdb = sdbHelper.getReadableDatabase();
			Cursor cursor = sdb.rawQuery("select name from sqlite_master where type='table' order by name", null);
			count = 0;
			while(cursor.moveToNext()){
				//遍历出表名
				//String name = cursor.getString(0);
				//Log.i("System.out", name);
				count++;
			}
			cursor.close();
			sdb.close();
			return count;
		}
		
	}
	
	class DBOpenHelper extends SQLiteOpenHelper{
		private static final int SDB_VERSION = 1;  //数据库版本号
	
		//参数2:数据库名,参数4:当前数据库版本号
		public DBOpenHelper(Context context, String name, SQLiteDatabase.CursorFactory factory, int version) {
			super(context, name, factory, version);
		}
		//一般只使用这个
		public DBOpenHelper(Context context){
			this(context, "ChainShop_Pos_5_0.db", null, SDB_VERSION);
		}
		@Override  //当数据库第一次创建时执行(创建数据表和添加一些必要的数据),new对象
		public void onCreate(SQLiteDatabase db) {
	
		}
		@Override  //数据库版本升级
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
	
		}
	}
	

}