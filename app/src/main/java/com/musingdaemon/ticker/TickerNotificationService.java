package com.musingdaemon.ticker;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@RequiresApi(api = Build.VERSION_CODES.O)
public class TickerNotificationService extends Service {

    private static final String INTERMITTENT_CHANNEL = "INTERMITTENT_CHANNEL";
    private static final String HEADLINE_URL = BuildConfig.HEADLINE_URL;
    private static final String HEADLINE_ELEMENT_MATCHER = BuildConfig.HEADLINE_PARENT_ELEMENT_MATCHER;
    private static final String ALPHA_VANTAGE_API_KEY = BuildConfig.ALPHA_VANTAGE_API_KEY;
    private static final String COMMODITIES_API_KEY = BuildConfig.COMMODITIES_API_KEY;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        doWork();

        return super.onStartCommand(intent, flags, startId);
    }

    private void doWork() {
        Executor executor = new Invoker();
        executor.execute(this::doNotification);
    }

    private void doNotification() {
        List<String> urls = new ArrayList<>();

        LocalDate now = LocalDate.now();

        String today = now.format(DateTimeFormatter.ISO_DATE);
        String yesterday = now.minus(1, ChronoUnit.DAYS).format(DateTimeFormatter.ISO_DATE);

        urls.add(String.format("https://commodities-api.com/api/fluctuation?access_key=%s&base=USD&symbols=WTIOIL%%2CBRENTOIL%%2CXAU%%2CXAG%%2CXPD%%2CLUMBER%%2CRUB&start_date=%s&end_date=%s", COMMODITIES_API_KEY, yesterday, today));
        urls.add(String.format("https://www.alphavantage.co/query?function=CURRENCY_EXCHANGE_RATE&from_currency=BTC&to_currency=USD&apikey=%s", ALPHA_VANTAGE_API_KEY));
        urls.add(String.format("https://www.alphavantage.co/query?function=TREASURY_YIELD&interval=daily&maturity=10year&apikey=%s", ALPHA_VANTAGE_API_KEY));
        urls.add(String.format("https://www.alphavantage.co/query?function=GLOBAL_QUOTE&symbol=SPY&apikey=%s", ALPHA_VANTAGE_API_KEY));

        List<String> stocks = getTickers(urls);

        Context context = getApplicationContext();

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        NotificationChannel tickerChannel = new NotificationChannel(INTERMITTENT_CHANNEL, "Quote Ticker", NotificationManager.IMPORTANCE_DEFAULT);

        notificationManager.createNotificationChannel(tickerChannel);

        Notification tickerNotification = new NotificationCompat.Builder(context, INTERMITTENT_CHANNEL)
                .setSmallIcon(R.drawable.notification)
                .setContentTitle("$")
                .setContentText(String.join(" ", stocks))
                .setPriority(NotificationManager.IMPORTANCE_DEFAULT)
                .build();

        NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(context);

        notificationManagerCompat.notify(1001, tickerNotification);

        if (HEADLINE_URL == null) {
            return;
        }

        String headlineResponse = getResponse(getURL(HEADLINE_URL));

        if (headlineResponse == null) {
            return;
        }

        int startHeadline = headlineResponse.indexOf(">", headlineResponse.indexOf("<a", headlineResponse.indexOf(HEADLINE_ELEMENT_MATCHER)));
        int endHeadline = headlineResponse.indexOf("<", startHeadline);

        String headline = headlineResponse.substring(startHeadline + 1, Math.min(endHeadline, startHeadline + 200));

        NotificationChannel headlineChannel = new NotificationChannel(INTERMITTENT_CHANNEL, "Headline", NotificationManager.IMPORTANCE_DEFAULT);

        notificationManager.createNotificationChannel(headlineChannel);

        Notification headlineNotification = new NotificationCompat.Builder(context, INTERMITTENT_CHANNEL)
                .setSmallIcon(R.drawable.notification)
                .setContentTitle("0")
                .setContentText(headline)
                .setPriority(NotificationManager.IMPORTANCE_DEFAULT)
                .build();

        notificationManagerCompat.notify(1001, headlineNotification);

    }

    private List<String> getTickers(List<String> urls) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return new ArrayList<>();
        }

        return urls.stream()
                .map(this::getURL)
                .map(this::getResponse)
                .filter(Objects::nonNull)
                .map(this::getTicker)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private URL getURL(String url) {
        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        return null;
    }

    private String getTicker(String response) {
        if (response == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return null;
        }

        try {

            if (response.contains("10-Year Treasury Constant Maturity Rate")) {
                int bracketIndex = response.indexOf("[");
                int openBraceIndex = response.indexOf("{", bracketIndex);
                int closeBraceIndex = response.indexOf("}", openBraceIndex);
                String treasuryResponse = response.substring(openBraceIndex, closeBraceIndex + 1);
                JSONObject jsonTreasury = new JSONObject(treasuryResponse);
                double value = Double.parseDouble(jsonTreasury.getString("value"));
                return String.format(Locale.US, "%.2f%%", value);
            }

            JSONObject fullResponse = new JSONObject(response);
            if (fullResponse.has("Global Quote")) {
                JSONObject globalQuote = fullResponse.getJSONObject("Global Quote");
                String symbol = globalQuote.getString("01. symbol");
                double price = globalQuote.getDouble("05. price");
                String percentChange = globalQuote.getString("10. change percent");
                double percentChangeNum = Double.parseDouble(percentChange.replaceFirst("%", ""));
                String percentChangeSign = percentChangeNum >= 0 ? "+" : "";
                return String.format(Locale.US, "%s:%.2f:%s%.1f%%", symbol, price, percentChangeSign, percentChangeNum);
            } else if (fullResponse.has("Realtime Currency Exchange Rate")) {
                JSONObject globalQuote = fullResponse.getJSONObject("Realtime Currency Exchange Rate");
                String fromSymbol = globalQuote.getString("1. From_Currency Code");
                String toSymbol = globalQuote.getString("3. To_Currency Code");
                double price = globalQuote.getDouble("5. Exchange Rate");
                return String.format(Locale.US, "%s:%.0f", fromSymbol, price);
            } else if (fullResponse.has("data") && fullResponse.getJSONObject("data").has("rates")) {
                JSONObject rates = fullResponse.getJSONObject("data").getJSONObject("rates");
                List<String> commodities = new ArrayList<>();
                commodities.add("BRENTOIL");
                commodities.add("LUMBER");
                commodities.add("XAG");
                commodities.add("XAU");

                List<String> commodityDisplays = new ArrayList<>();

                for (String commodity : commodities) {
                    JSONObject rate = rates.getJSONObject(commodity);
                    if (Objects.equals(commodity, "BRENTOIL")) {
                        commodity = "OIL";
                    }
                    if (Objects.equals(commodity, "LUMBER")) {
                        commodity = "LMBR";
                    }
                    if (Objects.equals(commodity, "XAG")) {
                        commodity = "SLV";
                    }
                    if (Objects.equals(commodity, "XAU")) {
                        commodity = "GLD";
                    }
                    double yesterdayPrice = 1.0 / rate.getDouble("start_rate");
                    double todayPrice = 1.0 / rate.getDouble("end_rate");
                    String priceChangeSign = (todayPrice - yesterdayPrice) < 0 ? "-" : "+";
                    double commodityPercentChange = rate.getDouble("change_pct");
                    String format;
                    if (todayPrice < 100) {
                        format = "%s:%.2f:%s%.1f%%";
                    } else if (todayPrice < 1000) {
                        format = "%s:%.1f:%s%.1f%%";
                    } else {
                        format = "%s:%.0f:%s%.1f%%";
                    }
                    commodityDisplays.add(String.format(Locale.US, format, commodity, todayPrice, priceChangeSign, commodityPercentChange));
                }

                return String.join(" ", commodityDisplays);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    private String getResponse(URL url) {
        if (url == null) {
            return null;
        }

        HttpURLConnection httpURLConnection = null;
        try {
            httpURLConnection = (HttpURLConnection) url.openConnection();
            InputStream inputStream = httpURLConnection.getInputStream();
            InputStream bufferedInputStream = new BufferedInputStream(inputStream);
            return readStream(bufferedInputStream);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (httpURLConnection != null) {
                httpURLConnection.disconnect();
            }
        }
        return null;
    }

    private String readStream(InputStream inputStream) throws IOException {
        int bufferSize = 1024;
        char[] buffer = new char[bufferSize];
        StringBuilder out = new StringBuilder();
        Reader in = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
        for (int numRead; (numRead = in.read(buffer, 0, buffer.length)) > 0; ) {
            out.append(buffer, 0, numRead);
        }
        return out.toString();
    }
}