package com.gbft.framework.demo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.gbft.framework.utils.Config;

public class DemoDataUtil {
    public static String httpGET(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String line;
                StringBuilder response = new StringBuilder();

                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                return response.toString();
            } else {
                System.out.println("HTTP GET request failed with response code: " + responseCode);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String httpPUT(String urlString, Map<String, String> formData) {
        StringBuilder urlParameters = new StringBuilder();

        for (Map.Entry<String, String> entry : formData.entrySet()) {
            if (urlParameters.length() != 0) {
                urlParameters.append("&");
            }
            urlParameters.append(entry.getKey()).append("=").append(entry.getValue());
        }

        byte[] postData = urlParameters.toString().getBytes(StandardCharsets.UTF_8);

        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setDoOutput(true);
            conn.setRequestMethod("PUT");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("charset", "utf-8");
            conn.setRequestProperty("Content-Length", Integer.toString(postData.length));

            try (OutputStream os = conn.getOutputStream()) {
                os.write(postData);
                os.flush();
            }

            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                System.out.println("Failed : HTTP error code : " + conn.getResponseCode());
                return null;
            }

            BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));

            StringBuilder output = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                output.append(line);
            }

            conn.disconnect();

            return output.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String getConfig(int unit_id) {
        return DemoDataUtil.httpGET("http://" + Config.string("demo.update_server") + ":" + Config.string("demo.update_port") + "/configs?unit_id=" + unit_id);
    }

    public static void putMetrics(MetricsDataItem metricsDataItem) {
        String urlString = "http://" + Config.string("demo.update_server") + ":" + Config.string("demo.update_port") + "/data";

        Map<String, String> formData = new HashMap<>();

        formData.put("metrics_name", metricsDataItem.getName());
        formData.put("value", metricsDataItem.getValue());
        formData.put("timestamp", metricsDataItem.getTimestamp() + "");
        formData.put("id", metricsDataItem.getId() + "");

        DemoDataUtil.httpPUT(urlString, formData);
    }
}
