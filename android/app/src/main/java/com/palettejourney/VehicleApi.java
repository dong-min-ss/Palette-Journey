package com.palettejourney;

import android.content.Context;
import android.util.Base64;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.security.KeyStore;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

final class VehicleApi {
    static String randomToken(){byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);StringBuilder b=new StringBuilder();for(byte v:bytes)b.append(String.format("%02x",v&255));return b.toString();}
    static JSONObject request(String path,String method,JSONObject body,String token)throws Exception {
        HttpURLConnection connection=(HttpURLConnection)new URL("http://10.0.2.2:3000"+path).openConnection();
        connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(5000);connection.setReadTimeout(15000);
        connection.setRequestMethod(method);connection.setRequestProperty("Authorization","Bearer "+token);
        try{
            if(body!=null){connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json");try(java.io.OutputStream out=connection.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}}
            int status=connection.getResponseCode();if(status!=200)throw new java.io.IOException("HTTP "+status);
            try(java.io.InputStream in=connection.getInputStream()){
                byte[] bytes=in.readNBytes(1048577);if(bytes.length>1048576)throw new java.io.IOException("RESPONSE_LIMIT");
                return new JSONObject(new String(bytes,StandardCharsets.UTF_8));
            }
        }finally{connection.disconnect();}
    }
    private static SecretKey key()throws Exception{
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);String alias="palette-connection";
        if(ks.containsAlias(alias))return (SecretKey)ks.getKey(alias,null);
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());return generator.generateKey();
    }
    static void save(Context context,String token)throws Exception{save(context,"participant",token);}
    static void save(Context context,String name,String token)throws Exception{
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
        String stored=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(cipher.doFinal(token.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);
        if(!context.getSharedPreferences("connection",0).edit().putString(name,stored).commit())throw new java.io.IOException("STORAGE_FAILED");
    }
    static String load(Context context)throws Exception{return load(context,"participant");}
    static String load(Context context,String name)throws Exception{
        String stored=context.getSharedPreferences("connection",0).getString(name,null);if(stored==null)return null;
        String[] parts=stored.split(":");Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),StandardCharsets.UTF_8);
    }
}
