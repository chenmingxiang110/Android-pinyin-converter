package com.mingxiangchen.autospeech;

import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import tensorMing_Fundation.NdArrayMath;
import tensorMing_Fundation.NdArrayUtils;
import tensorMing_Fundation.Variable;
import tensorMing_Learn.NeighboursKNNClassifier;

public class MainActivity extends AppCompatActivity {

    private HashMap<String, String> wordMap;
    private HashMap<String, Float> wordProb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        wordMap = loadWordMap();
        wordProb = loadWordProb();
        EditText et = (EditText) findViewById(R.id.pinyin_box);
        TextView tv = (TextView) findViewById(R.id.word_box);
        et.setMovementMethod(new ScrollingMovementMethod());
        tv.setMovementMethod(new ScrollingMovementMethod());

        ImageButton b = (ImageButton) findViewById(R.id.record_btn);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Button b = (Button) v;
                if (b.getText().equals("停止录音")) {
                    stopRecording();
                } else if (b.getText().equals("开始录音")){
                    startRecording();
                }
            }
        });
    }

    // 拼音文字转换模块 -- 开始分割线 --

    private HashMap<String, String> loadWordMap() {
        HashMap<String, String> result = new HashMap<String, String>();
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(getAssets().open("wordMap.txt")));
            String line;
            while ((line = br.readLine()) != null) {
                String[] linelist = line.trim().split(",");
                result.put(linelist[0], linelist[1]);
            }
        } catch (IOException e) {
            System.err.println("Caught IOException: " + e.getMessage());
        } finally {
            if (null != br) {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return result;
    }

    private HashMap<String, Float> loadWordProb() {
        HashMap<String, Float> result = new HashMap<String, Float>();
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(getAssets().open("wordProb.txt")));
            String line;
            while ((line = br.readLine()) != null) {
                String[] linelist = line.trim().split(",");
                result.put(linelist[0], Float.parseFloat(linelist[1]));
            }
        } catch (IOException e) {
            System.err.println("Caught IOException: " + e.getMessage());
        } finally {
            if (null != br) {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return result;
    }

    private String joinStringArray(String[] inputs) {
        if (inputs.length == 0) return "";
        if (inputs.length == 1) return inputs[0];
        StringBuilder builder = new StringBuilder();
        for(int i = 0 ; i<inputs.length-1 ; i++) {
            builder.append(inputs[i]);
            builder.append(" ");
        }
        builder.append(inputs[inputs.length-1]);
        String str = builder.toString();
        return str;
    }

    private HashMap<String, Float> getWords(String[] inputs) {
        HashMap<String, Float> result = new HashMap<String, Float>();
        if (inputs.length == 1) {
            if (wordMap.containsKey(inputs[0])) {
                result.put(wordMap.get(inputs[0]), wordProb.get(wordMap.get(inputs[0])));
            } else {
                result.put(inputs[0], (float)0.01);
            }
        } else {
            for (int i = 1 ; i < inputs.length+1 ; i++) {
                String s = joinStringArray(Arrays.copyOfRange(inputs, 0, i));
                float prob;
                String word;
                if (wordMap.containsKey(s)) {
                    word = wordMap.get(s);
                    prob = wordProb.get(word)*word.length();
                } else {
                    if (i>1) {
                        continue;
                    } else {
                        word = " "+inputs[0]+" ";
                        prob = (float)0.01;
                    }
                }
                if (i != inputs.length) {
                    HashMap<String, Float> wordsAfter = getWords(Arrays.copyOfRange(inputs, i, inputs.length));

                    for (String k : wordsAfter.keySet()) {
                        String newKey = word+k;
                        float newProb = prob*wordsAfter.get(k);
                        result.put(newKey, newProb);
                    }
                } else {
                    result.put(word, prob);
                }
            }
        }
        return result;
    }

    private String getMaxProb(HashMap<String, Float> stringProb) {
        String result = "";
        if (stringProb.size()>0) {
            float maxScore = (float)-1;
            for (String s : stringProb.keySet()) {
                System.out.println(s+stringProb.get(s));
                if (stringProb.get(s)>maxScore) {
                    result = s;
                    maxScore = stringProb.get(s);
                }
            }
        } else {
            result = "-- 无有效拼音 --";
        }
        return result;
    }

    public void transformPinyin(View view) {
        EditText et = (EditText) findViewById(R.id.pinyin_box);
        TextView tv = (TextView) findViewById(R.id.word_box);
        String[] inputs = et.getText().toString().trim().split("\n");
        StringBuilder sb = new StringBuilder();
        for (String input : inputs) {
            String[] currentInput = input.split(" ");
            HashMap<String, Float> stringProb = getWords(currentInput);
            sb.append(getMaxProb(stringProb));
            sb.append("\n");
        }

        tv.setText(sb.toString());
    }

    // 拼音文字转换模块 -- 结束分割线 --
    // 录音模块 -- 开始分割线 --

    private void startRecording() {
        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                RECORDER_SAMPLERATE, RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING, bufferSize);

        int i = recorder.getState();
        if (i == 1)
            recorder.startRecording();
        isRecording = true;
        recordingThread = new Thread(new Runnable() {

            @Override
            public void run() {
                writeAudioDataToFile();
            }
        }, "AudioRecorder Thread");
        recordingThread.start();
    }

    private void writeAudioDataToFile(){
        byte data[] = new byte[bufferSize];
        String filepath = Environment.getExternalStorageDirectory().getPath();
        String filename = filepath + "/" + AUDIO_RECORDER_FOLDER + "/" + AUDIO_RECORDER_TEMP_FILE;
        FileOutputStream os = null;

        try {
            os = new FileOutputStream(filename);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        int read = 0;
        if (null != os) {
            while (isRecording) {
                read = recorder.read(data, 0, bufferSize);

                if (AudioRecord.ERROR_INVALID_OPERATION != read) {
                    try {
                        os.write(data);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            try {
                os.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void stopRecording(){
        if (null != recorder) {
            isRecording = false;

            int i = recorder.getState();
            if (i == 1)
                recorder.stop();
            recorder.release();
            recorder = null;
            recordingThread = null;
        }
    }

    private void deleteTempFile(){
        File file = new File(getTempFilename());
        file.delete();
    }

    private void recognize() {
        String inFilename = tempFileName;
        String outFilename = getFilename();
        File file = new File(outFilename);
        file.delete();
        FileInputStream in = null;
        FileOutputStream out = null;
        long totalAudioLen = 0;
        long totalDataLen = totalAudioLen + 36;
        long longSampleRate = RECORDER_SAMPLERATE;
        int channels = 2;
        long byteRate = RECORDER_BPP * RECORDER_SAMPLERATE * channels / 8;
        byte[] data = new byte[bufferSize];

        try {
            in = new FileInputStream(inFilename);
            out = new FileOutputStream(outFilename);
            totalAudioLen = in.getChannel().size();
            totalDataLen = totalAudioLen + 36;
            WriteWaveFileHeader(out, totalAudioLen, totalDataLen,
                    longSampleRate, channels, byteRate);
            while (in.read(data) != -1) {
                out.write(data);
            }

            in.close();
            out.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void WriteWaveFileHeader(
            FileOutputStream out, long totalAudioLen,
            long totalDataLen, long longSampleRate, int channels,
            long byteRate) throws IOException {

        byte[] header = new byte[44];

        header[0] = 'R'; // RIFF/WAVE header
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f'; // 'fmt ' chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16; // 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1; // format = 1
        header[21] = 0;
        header[22] = (byte) channels;
        header[23] = 0;
        header[24] = (byte) (longSampleRate & 0xff);
        header[25] = (byte) ((longSampleRate >> 8) & 0xff);
        header[26] = (byte) ((longSampleRate >> 16) & 0xff);
        header[27] = (byte) ((longSampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (2 * 16 / 8); // block align
        header[33] = 0;
        header[34] = RECORDER_BPP; // bits per sample
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);

        out.write(header, 0, 44);
    }

    // 录音模块 -- 开始分割线 --
}
