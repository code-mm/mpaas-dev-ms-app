package org.ms.dev.app;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.alipay.android.phone.scancode.export.ScanCallback;
import com.alipay.android.phone.scancode.export.ScanRequest;
import com.alipay.android.phone.scancode.export.adapter.MPScan;
import com.github.graphql.QueryRoot;
import com.github.graphql.QueryRootQuery;
import com.github.graphql.QueryRootQueryDefinition;
import com.github.graphql.UserQuery;
import com.github.graphql.UserQueryDefinition;

import org.ms.dev.R;
import org.ms.graphql.GraphClient;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import io.reactivex.SingleObserver;
import io.reactivex.disposables.Disposable;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.RSocketFactory;
import io.rsocket.frame.decoder.PayloadDecoder;
import io.rsocket.transport.netty.client.TcpClientTransport;
import io.rsocket.util.DefaultPayload;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import reactor.core.publisher.Mono;

public class MainActivity extends AppCompatActivity {


    private static final String TAG = "MainActivity";


    private TextView textViewHello;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textViewHello = findViewById(R.id.textViewHello);

        ScanRequest scanRequest = new ScanRequest();
        scanRequest.setScanType(ScanRequest.ScanType.QRCODE);

        textViewHello.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MPScan.startMPaasScanActivity(MainActivity.this, scanRequest, (isProcessed, result) -> {
                    String s = result.getData().toString();

                    Toast.makeText(MainActivity.this, "" + s, Toast.LENGTH_SHORT).show();


                });
            }
        });


        Mono<RSocket> start = RSocketFactory.connect().frameDecoder(PayloadDecoder.ZERO_COPY)
                .dataMimeType("application/json")
                .metadataMimeType("message/x.rsocket.routing.v0")
                .transport(TcpClientTransport.create("192.168.0.43", 7000))
                .start();


        start.blockOptional().ifPresent(new Consumer<RSocket>() {
            @Override
            public void accept(RSocket rSocket) {

                byte[] bytes = "hello".getBytes();

                byte[] buf = new byte[256];
                buf[0] = 5;
                int i = 1;
                for (byte it : bytes) {
                    buf[i++] = it;
                }

                rSocket.requestResponse(DefaultPayload.create("{\"origin\":\"Client\",\"interaction\":\"Request\"}".getBytes(), buf)).subscribe(new Consumer<Payload>() {
                    @Override
                    public void accept(Payload payload) {


                        System.out.println("ThreadName : " + Thread.currentThread().getName());


                        System.out.println(payload.getDataUtf8());
                        System.out.println(payload.getMetadataUtf8());

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {

                                Toast.makeText(MainActivity.this, "" + payload.getDataUtf8(), Toast.LENGTH_SHORT).show();
                            }
                        });

                    }
                });

            }
        });

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Authorization", "token 8861fc978f783bd2c4603aa51229208f30f51bf1");

        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .addInterceptor(new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
                .addInterceptor(new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.HEADERS))
                .addInterceptor(new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
                .addInterceptor(new LogInterceptor())
                .build();


        GraphClient graphqlClient = GraphClient.builder().setUrl("https://api.github.com/graphql")
                .setHeaders(headers)
                .setHttpClient(okHttpClient)
                .build();

        graphqlClient.queryGraph(com.github.graphql.Operations.query(new QueryRootQueryDefinition() {
            @Override
            public void define(QueryRootQuery queryRootQuery) {

                queryRootQuery.viewer(new UserQueryDefinition() {
                    @Override
                    public void define(UserQuery userQuery) {

                        userQuery.login();
                        userQuery.email();
                    }
                });

            }
        })).subscribe(new SingleObserver<QueryRoot>() {
            @Override
            public void onSubscribe(Disposable d) {

            }

            @Override
            public void onSuccess(QueryRoot queryRoot) {


                String login = queryRoot.getViewer().getLogin();
                String email = queryRoot.getViewer().getEmail();


                Log.e(TAG, "onSuccess login : " + login);
                Log.e(TAG, "onSuccess email : " + email);

            }

            @Override
            public void onError(Throwable e) {
                e.printStackTrace();
            }
        });

    }

}
