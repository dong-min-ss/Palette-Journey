package com.palettejourney;

import android.content.Context;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;

/** Web canvas/SVG fidelity without credentials, networking, editing or a JavaScript bridge. */
final class JourneyBookRenderer {
    private static String asset(Context context,String path)throws Exception{try(java.io.InputStream in=context.getAssets().open(path)){return Base64.encodeToString(in.readAllBytes(),Base64.NO_WRAP);}}
    static JSONObject assetsFor(Context context,JSONArray stickers)throws Exception{
        JSONObject assets=new JSONObject();JSONArray registry;try(java.io.InputStream in=context.getAssets().open("stickers/catalog.json")){registry=new JSONArray(new String(in.readAllBytes(),StandardCharsets.UTF_8));}
        if(stickers!=null)for(int i=0;i<stickers.length();i++){
            JSONObject item=stickers.getJSONObject(i);String id=item.optString("stickerId",item.optString("id")),name=null;
            for(int j=0;j<registry.length();j++){JSONObject known=registry.getJSONObject(j);if(id.equals(known.getString("id"))){String path=known.getString("asset");if(!path.matches("/assets/stickers/[a-z0-9-]+\\.svg"))throw new java.io.IOException("INVALID_ASSET");name=path.substring("/assets/stickers/".length(),path.length()-4);break;}}
            if(name==null)throw new java.io.IOException("UNKNOWN_STICKER");
            if(!assets.has(id))assets.put(id,new JSONObject().put("base",asset(context,"stickers/"+name+".svg")).put("outline",asset(context,"stickers/"+name+"-outline.svg")));
        }
        return assets;
    }
    static String html(Context context,JSONObject page)throws Exception{
        JSONObject assets=assetsFor(context,page.optJSONArray("stickers"));
        String template;try(java.io.InputStream in=context.getAssets().open("journey-book.html")){template=new String(in.readAllBytes(),StandardCharsets.UTF_8);}
        String data=new JSONObject().put("page",page).put("assets",assets).toString();return template.replace("__PAGE_DATA__",Base64.encodeToString(data.getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP));
    }
}
