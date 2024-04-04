package cine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CrptApi {
    private final Queue<Long> window = new ArrayDeque<>();
    private final long windowSizeMillis;
    private final int maxRequests;
    private final Logger logger = Logger.getLogger(CrptApi.class.getName());

    public CrptApi(int maxRequests, TimeUnit timeUnit, long windowSize) {
        this.maxRequests = maxRequests;
        this.windowSizeMillis = timeUnit.toMillis(windowSize);
    }

    public void createDocument(Document document, String signature) {
        long currentTime = System.currentTimeMillis();
        synchronized (window) {
            // Пока размер окна превышает максимальное количество запросов,
            // пытаемся очищать старые запросы из окна и делаем задержку
            while (window.size() <= maxRequests) {
                try {
                    window.wait(windowSizeMillis / 2);
                } catch (InterruptedException e) {
                    logger.log(Level.WARNING, "Thread interrupted while waiting", e);
                    Thread.currentThread().interrupt();
                }
                while (!window.isEmpty() && currentTime - window.peek() > windowSizeMillis) {
                    // Удаляем старые запросы
                    window.poll();
                    // Добавляем текущий запрос в окно
                }
            }
            window.offer(currentTime);
        }

        // Отправка запроса на создание документа
        try {
            String url = "https://ismp.crpt.ru/api/v3/lk/documents/create";
            CloseableHttpClient httpClient = HttpClients.createDefault();
            HttpPost request = new HttpPost(url);
            request.setHeader("Content-Type", "application/json");

            // JSON-объект с данными документа
            ObjectMapper objectMapper = new ObjectMapper();
            ObjectNode jsonPayload = objectMapper.createObjectNode();
            jsonPayload.putPOJO("description", document);
            jsonPayload.put("signature", signature);

            StringEntity entity = new StringEntity(jsonPayload.toString(), ContentType.APPLICATION_JSON);
            request.setEntity(entity);

            CloseableHttpResponse response = httpClient.execute(request);
            try {
                HttpEntity responseEntity = response.getEntity();
            } finally {
                response.close();
                httpClient.close();
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to send HTTP request", e);
        }
    }

    public static void main(String[] args) {
        CrptApi crptApi = new CrptApi(10, TimeUnit.SECONDS, 1);
        Document document = new Document("1234567890");
        String signature = "example_signature";
        crptApi.createDocument(document, signature);
    }
}
