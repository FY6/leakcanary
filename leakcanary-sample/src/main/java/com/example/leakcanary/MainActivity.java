/*
 * Copyright (C) 2015 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.leakcanary;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

public class MainActivity extends Activity {

    private HttpRequestHelper httpRequestHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

        View button = findViewById(R.id.async_work);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startAsyncWork();
            }
        });

        httpRequestHelper = (HttpRequestHelper) getLastNonConfigurationInstance();
        if (httpRequestHelper == null) {
            httpRequestHelper = new HttpRequestHelper(button);
        }


        new Thread() {
            @Override
            public void run() {
                monitorClearedResources();
            }
        }.start();

        new Thread() {
            @Override
            public void run() {
                while (true) {
                    new WeakReference<WeakRefrencesBean>(new WeakRefrencesBean("aaaa"), referenceQueue);
                }
            }
        }.start();

    }


    /**
     * 监控对象被回收，因为如果被回收就会就如与之关联的队列中
     */
    private void monitorClearedResources() {
        Log.e("tag", "start monitor");
        try {
            int n = 0;
            WeakReference k;
            while ((k = (WeakReference) referenceQueue.remove()) != null) {
                Log.e("tag", (++n) + "回收了:" + k + "   object: " + k.get());
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    private ReferenceQueue<WeakRefrencesBean> referenceQueue = new ReferenceQueue<>();

    class WeakRefrencesBean {
        private String name;

        public WeakRefrencesBean(String name) {
            this.name = name;
        }
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        return httpRequestHelper;
    }

    @SuppressLint("StaticFieldLeak")
    void startAsyncWork() {
        // This runnable is an anonymous class and therefore has a hidden reference to the outer
        // class MainActivity. If the activity gets destroyed before the thread finishes (e.g. rotation),
        // the activity instance will leak.
        Runnable work = new Runnable() {
            @Override
            public void run() {
                // Do some slow work in background
                SystemClock.sleep(20000);
            }
        };
        new Thread(work).start();
    }
}


