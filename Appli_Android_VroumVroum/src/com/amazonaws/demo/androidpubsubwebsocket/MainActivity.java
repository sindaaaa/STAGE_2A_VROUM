package com.amazonaws.demo.androidpubsubwebsocket;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class MainActivity extends Activity implements View.OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getIntent();

        Button btn = (Button) findViewById(R.id.button);
        btn.setOnClickListener(this);

        Button btn2 = (Button) findViewById(R.id.button2);
        btn2.setOnClickListener(this);


    }
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.button:
                //Button btn = (Button) findViewById(R.id.button);
                //btn.setText("READY !");
                // Création d’une intention
                Intent playIntent = new Intent(this, PubSubActivity.class);
                // Ajout d’un parametre à l’intention
                //playIntent.putExtra("name", "Mon NOM");
                startActivity(playIntent);
                break;
            case R.id.button2:
                //Button btn2 = (Button) findViewById(R.id.button2);
                //btn2.setText("READY !");
                // Création d’une intention
                Intent playIntent1 = new Intent(this, MapActivity.class);
                // Ajout d’un parametre à l’intention
                //playIntent.putExtra("name", "Mon NOM");
                startActivity(playIntent1);

                break;
        }
    }

}