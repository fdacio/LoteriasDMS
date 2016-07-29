package br.com.daciosoftware.loteriasdms.processaarquivo;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.Calendar;

import br.com.daciosoftware.loteriasdms.R;
import br.com.daciosoftware.loteriasdms.StyleTypeSorteio;
import br.com.daciosoftware.loteriasdms.TypeSorteio;
import br.com.daciosoftware.loteriasdms.dao.SorteioDAO;
import br.com.daciosoftware.loteriasdms.util.Constantes;
import br.com.daciosoftware.loteriasdms.util.DateUtil;
import br.com.daciosoftware.loteriasdms.util.DecompressFile;
import br.com.daciosoftware.loteriasdms.util.DialogBox;
import br.com.daciosoftware.loteriasdms.util.DownloadFile;
import br.com.daciosoftware.loteriasdms.util.FileUtil;
import br.com.daciosoftware.loteriasdms.util.LogProcessamento;
import br.com.daciosoftware.loteriasdms.util.filedialog.FileDialog;

public class ProcessaArquivoActivity extends AppCompatActivity {

    private TextView textViewLabelFile;
    private EditText editTextArquivo;
    private TypeSorteio typeSorteio;
    private ProgressDialog progressDialog;
    private DownloadFile downloadFile;
    private DecompressFile decompressFile;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_processar_arquivo);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        View layout = findViewById(R.id.layout_processar_arquivo);
        typeSorteio = (TypeSorteio) getIntent().getSerializableExtra(Constantes.TYPE_SORTEIO);
        StyleTypeSorteio styleTypeSorteio = new StyleTypeSorteio(this, layout);
        styleTypeSorteio.setStyleHeader(typeSorteio);

        textViewLabelFile = (TextView) findViewById(R.id.textViewLabelFile);
        editTextArquivo = (EditText) findViewById(R.id.editTextArquivo);
        ImageButton imageButtonArquivo = (ImageButton) findViewById(R.id.imageButtonArquivo);
        Button buttonProcessarArquivo = (Button) findViewById(R.id.buttonProcessarArquivo);
        Button buttonBaixarArquivo = (Button) findViewById(R.id.buttonBaixarArquivo);
        styleTypeSorteio.setStyleButton(typeSorteio);

        imageButtonArquivo.setOnClickListener(new OnClickListenerButtonArquivo());
        buttonProcessarArquivo.setOnClickListener(new OnClickListenerButtonProcessarArquivo());
        buttonBaixarArquivo.setOnClickListener(new OnClickListenerBaixaArquivo());

    }

    /**
     * Subclasse privada que implementar o OnClickListener do Botao com lupa.
     * Esse botão abri um FileDialog para seleção de um arquivo HTML para
     * o processamento dos sorteios
     */
    private class OnClickListenerButtonArquivo implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            FileDialog fileDialog = new FileDialog(ProcessaArquivoActivity.this, FileDialog.FileDialogType.OPEN_FILE);
            fileDialog.setStartPath(FileUtil.getDefaultDirectory("LoteriasDMS"));
            fileDialog.setFormaterFilter(new String[]{"html", "htm"});
            fileDialog.show();
        }
    }


    private class OnClickListenerBaixaArquivo implements View.OnClickListener {
        @Override
        public void onClick(View v) {

            if (isNetwork()) {
                String defaulDirectory = FileUtil.getDefaultDirectory("LoteriasDMS");
                String outFile = defaulDirectory + "/" + getNomeArquivoZip(typeSorteio);
                downloadFile = new DownloadFile();
                decompressFile = new DecompressFile();
                new BaixarArquivoZipTask().execute(getLinkBaixar(typeSorteio), outFile);
            } else {
                new DialogBox(ProcessaArquivoActivity.this,
                        DialogBox.DialogBoxType.INFORMATION, "Error",
                        getResources().getString(R.string.error_conexao)
                ).show();
            }
        }
    }

    /**
     * Processamento do Arquivo
     */
    private class OnClickListenerButtonProcessarArquivo implements View.OnClickListener {
        @Override
        public void onClick(View v) {

            String filePath = editTextArquivo.getText().toString();
            if (filePath.equals("")) {
                new DialogBox(ProcessaArquivoActivity.this, DialogBox.DialogBoxType.INFORMATION, "Error", getResources().getString(R.string.error_informe_arquivo)).show();
                return;
            }

            File file = new File(filePath);
            if (!file.exists()) {
                new DialogBox(ProcessaArquivoActivity.this, DialogBox.DialogBoxType.INFORMATION, "Error", getResources().getString(R.string.error_arquivo_invalido)).show();
                return;
            }

            new ProcessarArquivoHtml().execute(filePath);
            //Aqui executar AsyncTask para processar o arquivo
        }
    }

    /*
    Verifiaca o status da conexão
     */
    private boolean isNetwork() {
        ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    private String getLinkBaixar(TypeSorteio typeSorteio) {
        SharedPreferences sharedPreferences = getSharedPreferences(Constantes.SHARED_PREF, MODE_PRIVATE);

        switch (typeSorteio) {
            case MEGASENA:
                return sharedPreferences.getString(Constantes.URL_ARQUIVO_RESULTADOS_MEGASENA, Constantes.URL_ARQUIVO_RESULTADOS_MEGASENA_DEFAULT);

            case LOTOFACIL:
                return sharedPreferences.getString(Constantes.URL_ARQUIVO_RESULTADOS_LOTOFACIL, Constantes.URL_ARQUIVO_RESULTADOS_LOTOFACIL_DEFAULT);

            case QUINA:
                return sharedPreferences.getString(Constantes.URL_ARQUIVO_RESULTADOS_QUINA, Constantes.URL_ARQUIVO_RESULTADOS_QUINA_DEFAULT);

            default:
                return "www.cef.gov.br/loterias";
        }
    }

    private String getNomeArquivoZip(TypeSorteio typeSorteio) {
        String time = DateUtil.timeToString(Calendar.getInstance());
        switch (typeSorteio) {
            case MEGASENA:
                return "megasena_" + time + ".zip";

            case LOTOFACIL:
                return "lotofacil_" + time + ".zip";

            case QUINA:
                return "quina_" + time + ".zip";

            default:
                return "arquivo_" + time + ".zip";
        }
    }

    private String getNomeArquivoHtml(TypeSorteio typeSorteio) {
        String time = DateUtil.timeToString(Calendar.getInstance());
        switch (typeSorteio) {
            case MEGASENA:
                return "megasena_" + time + ".htm";

            case LOTOFACIL:
                return "lotofacil_" + time + ".htm";

            case QUINA:
                return "quina_" + time + ".htm";

            default:
                return "arquivo_" + time + ".htm";
        }
    }

    private String getTituloArquivoHtml(TypeSorteio typeSorteio) {
        switch (typeSorteio) {
            case MEGASENA:
                return "mega-sena";

            case LOTOFACIL:
                return "lotofácil";

            case QUINA:
                return "quina";

            default:
                return "titulo";
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == FileDialog.FILE_DIALOG && resultCode == RESULT_OK) {
            String filePath = intent.getStringExtra(FileDialog.RESULT_PATH);
            editTextArquivo.setText(filePath);
        }
    }


    /**
     * Subclasse privada que implementa o processo de baixa e descopressão do
     * arquivo com os resultados dos sorteios.
     * Essa subclasse extende de AsyncTask para realiazar o processo
     * em um outra Thread que não seja a Main Thread
     */
    private class BaixarArquivoZipTask extends AsyncTask<String, String, String> {
        private boolean running = true;
        private String pathFileHtml;

        @Override
        protected String doInBackground(String... params) {

            while (running) {

                String url = params[0];
                String outFile = params[1];
                try {
                    downloadFile.downloadFileBinary(url, outFile);
                } catch (IOException e) {
                    return "Erro ao baixar o arquivo: " + e.getMessage();
                }

                try {
                    publishProgress("Descomprimindo arquivo. Aguarde...");
                    String inFileDecompress = outFile;
                    String outFileHtml = new File(outFile).getParent() + "/" + getNomeArquivoHtml(typeSorteio);
                    pathFileHtml = decompressFile.unzip(inFileDecompress, outFileHtml);

                } catch (IOException e2) {
                    return "Erro ao descomprimir o aquivo:" + e2.getMessage();
                }


                if (isCancelled()) {
                    return "Processo cancelado.";
                }

                return "Processo concluído com sucesso.";
            }
            return "Processo cancelado.";
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressDialog = new ProgressDialog(ProcessaArquivoActivity.this);
            progressDialog.setMessage("Baixando arquivo. Aguarde...");
            progressDialog.setCancelable(true);
            progressDialog.setIndeterminate(true);
            progressDialog.setOnCancelListener(new cancelTaskBaixarArquivoZip(this));
            progressDialog.show();
        }


        @Override
        protected void onPostExecute(String retorno) {
            progressDialog.dismiss();
            editTextArquivo.setText(pathFileHtml);
            try {
                downloadFile.closeResources();
                decompressFile.closeResources();
            } catch (IOException e) {
                e.printStackTrace();
            }
            new DialogBox(ProcessaArquivoActivity.this, DialogBox.DialogBoxType.INFORMATION, "Baixar Arquivo", retorno).show();

        }

        @Override
        protected void onCancelled() {
            Toast.makeText(ProcessaArquivoActivity.this, "Processo cancelado!", Toast.LENGTH_SHORT).show();
            running = false;
            super.onCancelled();
        }

        @Override
        protected void onProgressUpdate(String... msgs) {
            String newMsg = msgs[0];
            progressDialog.setMessage(newMsg);


        }

    }


    /**
     * Subclasse privada que implementa o cancelamento do processo de download
     * e descompressão do arquivo
     * Ela e instanciada no onCancelListener do progressDialog e recebe no
     * construtor a AsyncTask a ser cancelada, no caso de BaixarArquivoZipTask
     */
    private class cancelTaskBaixarArquivoZip implements DialogInterface.OnCancelListener {

        private AsyncTask task;

        public cancelTaskBaixarArquivoZip(AsyncTask task) {
            this.task = task;
        }

        @Override
        public void onCancel(DialogInterface dialog) {
            new DialogBox(ProcessaArquivoActivity.this,
                    DialogBox.DialogBoxType.QUESTION,
                    "Processar Arquivo",
                    "Deseja cancelar o processo?",
                    new DialogInterface.OnClickListener() {//Resposta SIM do DialogBox Question
                        @Override
                        public void onClick(DialogInterface dialog, int which) {

                            try {
                                downloadFile.closeResources();
                                decompressFile.closeResources();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }

                            task.cancel(true);
                        }
                    },
                    new DialogInterface.OnClickListener() {//Resposta NÃO do DialogBox Question
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            progressDialog.show();
                        }
                    }

            ).show();
        }
    }

    private boolean isRowValid(String col) {
        try {
            Integer.parseInt(col);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private class ProcessarArquivoHtml extends AsyncTask<String, String, String> {

        private boolean running = true;

        @Override
        protected String doInBackground(String... params) {
            String pathFileHtml = params[0];
            SorteioDAO sorteioDAO = SorteioDAO.getDAO(getApplicationContext(), typeSorteio);

            try {

                File fileHtml = new File(pathFileHtml);

                float fileSize = FileUtil.getSizeMBytes(fileHtml);
                publishProgress(new DecimalFormat("0.00").format(fileSize)+"MB");

                Document doc = Jsoup.parse(fileHtml, "ISO-8859-1");

                String titleDoc = doc.title();
                String titleJogo = getTituloArquivoHtml(typeSorteio);

                if (!titleDoc.toLowerCase().contains(titleJogo.substring(0, 4))) {
                    return "Arquivo de outro Jogo: " + titleDoc;
                }

                Element table = doc.select("table").first();
                Elements trows = table.select("tr");
                trows.remove(0);
                for (Element row : trows) {
                    Elements tds = row.select("td");
                    String valueTd = row.child(0).text();
                    if (isRowValid(valueTd)) {
                        LogProcessamento.registraLog(tds.html());
                        try {
                            sorteioDAO.insertSorteioFromTrow(tds);
                        } catch (ParseException e) {
                            LogProcessamento.registraLog(e.getMessage());                        }
                    }
                    if (!running) {
                        return "Processamento cancelado";
                    }
                }

            } catch (NumberFormatException | IOException | OutOfMemoryError e) {
                return "Erro ao processar arquivo: " + e.getMessage();
            }
            return "Processamento concluído com sucesso";
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressDialog = new ProgressDialog(ProcessaArquivoActivity.this);
            progressDialog.setMessage("Processando arquivo. Aguarde...");
            progressDialog.setCancelable(true);
            progressDialog.setIndeterminate(true);
            progressDialog.setOnCancelListener(new cancelTaskProcessamentoHtml(this));
            progressDialog.show();
        }

        @Override
        protected void onPostExecute(String retorno) {
            progressDialog.dismiss();
            editTextArquivo.setText("");
            new DialogBox(ProcessaArquivoActivity.this, DialogBox.DialogBoxType.INFORMATION, "Processar Arquivo", retorno).show();

        }

        @Override
        protected void onCancelled() {
            running = false;
            Toast.makeText(ProcessaArquivoActivity.this, "Processo cancelado!", Toast.LENGTH_SHORT).show();
            super.onCancelled();

        }

        @Override
        protected void onProgressUpdate(String... msgs) {
            String sizeFile = msgs[0];
            textViewLabelFile.setText(textViewLabelFile.getText().toString()+"("+String.valueOf(sizeFile)+")");
        }


    }


    private class cancelTaskProcessamentoHtml implements DialogInterface.OnCancelListener {

        private AsyncTask task;

        public cancelTaskProcessamentoHtml(AsyncTask task) {
            this.task = task;
        }

        @Override
        public void onCancel(DialogInterface dialog) {
            new DialogBox(ProcessaArquivoActivity.this,
                    DialogBox.DialogBoxType.QUESTION,
                    "Processar Arquivo",
                    "Deseja cancelar o processo?",
                    new DialogInterface.OnClickListener() {//Resposta SIM do DialogBox Question
                        @Override
                        public void onClick(DialogInterface dialog, int which) {

                            task.cancel(true);
                        }
                    },
                    new DialogInterface.OnClickListener() {//Resposta NÃO do DialogBox Question
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            progressDialog.show();
                        }
                    }

            ).show();
        }
    }

}
