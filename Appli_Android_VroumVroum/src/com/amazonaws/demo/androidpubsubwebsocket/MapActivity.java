package com.amazonaws.demo.androidpubsubwebsocket;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class MapActivity extends Activity implements View.OnClickListener  {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.map_activity);

        Button btn = (Button) findViewById(R.id.button4);
        btn.setOnClickListener(this);

        getIntent();
    }
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.button4:
                //Button btn = (Button) findViewById(R.id.button);
                //btn.setText("READY !");
                // Création d’une intention
                Intent playIntent = new Intent(this, MainActivity.class);
                // Ajout d’un parametre à l’intention
                //playIntent.putExtra("name", "Mon NOM");
                startActivity(playIntent);
                break;
        }
    }

}
