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

public class CrptApi {
    private final Queue<Long> window = new ArrayDeque<>();
    private final long windowSizeMillis;
    private final int maxRequests;
    private final Object lock = new Object();

    public CrptApi(int maxRequests, TimeUnit timeUnit, long windowSize) {
        this.maxRequests = maxRequests;
        this.windowSizeMillis = timeUnit.toMillis(windowSize);
    }

    public void createDocument(Document document, String signature) {
        long currentTime = System.currentTimeMillis();
        synchronized (lock) {
            // Удаляем старые запросы из окна
            while (!window.isEmpty() && currentTime - window.peek() > windowSizeMillis) {
                window.poll();
            }
            // Если окно заполнено, останавливаем обработку запроса
            while (window.size() >= maxRequests) {
                try {
                    lock.wait(); // Ждем, пока не освободится место
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            // Добавляем текущий запрос
            window.offer(currentTime);
        }

        // Отправка запроса на создание документа
        try {
            String url = "https://ismp.crpt.ru/api/v3/lk/documents/create";
            CloseableHttpClient httpClient = HttpClients.createDefault();
            HttpPost request = new HttpPost(url);
            request.setHeader("Content-Type", "application/json");

            // Строим JSON-объект с данными документа
            ObjectMapper objectMapper = new ObjectMapper();
            ObjectNode jsonPayload = objectMapper.createObjectNode();
            jsonPayload.putPOJO("description", document);
            jsonPayload.put("signature", signature);

            StringEntity entity = new StringEntity(jsonPayload.toString(), ContentType.APPLICATION_JSON);
            request.setEntity(entity);

            // Отправляем запрос
            CloseableHttpResponse response = httpClient.execute(request);
            try {
                HttpEntity responseEntity = response.getEntity();
                System.out.println("Response status: " + response.getStatusLine());
            } finally {
                response.close();
                httpClient.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        CrptApi crptApi = new CrptApi(10, TimeUnit.SECONDS, 1);
        Document document = new Document("1234567890");
        String signature = "example_signature";
        crptApi.createDocument(document, signature);
    }
}
