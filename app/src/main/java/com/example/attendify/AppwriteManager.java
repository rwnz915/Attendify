package com.example.attendify;

import android.content.Context;

import java.io.File;
import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.json.JSONObject;

public class AppwriteManager {

    private static final String ENDPOINT  = "https://cloud.appwrite.io/v1";
    private static final String PROJECT_ID = "69f1b8c9002521046c34";
    private static final String BUCKET_ID  = "69f1b8e5001613e2e7c6";

    private final OkHttpClient httpClient;

    public AppwriteManager(Context context) {
        httpClient = new OkHttpClient();
    }

    // CALLBACK INTERFACE
    public interface UploadCallback {
        void onSuccess(String fileId);
        void onError(Exception e);
    }

    // UPLOAD IMAGE
    public void uploadImage(File file, UploadCallback callback) {

        String uniqueId = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 20);

        RequestBody fileBody = RequestBody.create(file, MediaType.parse("image/*"));

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("fileId", uniqueId)
                .addFormDataPart("file", file.getName(), fileBody)
                .build();

        Request request = new Request.Builder()
                .url(ENDPOINT + "/storage/buckets/" + BUCKET_ID + "/files")
                .addHeader("X-Appwrite-Project", PROJECT_ID)
                .post(requestBody)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    String responseBody = response.body().string();
                    JSONObject json = new JSONObject(responseBody);

                    if (response.isSuccessful()) {
                        String fileId = json.getString("$id");
                        callback.onSuccess(fileId);
                    } else {
                        String message = json.optString("message", "Upload failed");
                        callback.onError(new Exception(message));
                    }
                } catch (Exception e) {
                    callback.onError(e);
                }
            }
        });
    }

    // GET IMAGE URL
    public String getFileUrl(String fileId) {
        return ENDPOINT +
                "/storage/buckets/" + BUCKET_ID +
                "/files/" + fileId +
                "/view?project=" + PROJECT_ID;
    }
}