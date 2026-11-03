package com.palettejourney;

import android.content.Context;
import android.util.Base64;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;

/** Same read-only catalog UI as the web; only packaged scripts/images, never credentials. */
final class CollectionRenderer {
    private static String read(Context c,String path)throws Exception{try(java.io.InputStream in=c.getAssets().open(path)){return new String(in.readAllBytes(),StandardCharsets.UTF_8);}}
    static String encoded(JSONObject data){return Base64.encodeToString(data.toString().getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP);}
    static String decode(String encoded){return "JSON.parse(new TextDecoder().decode(Uint8Array.from(atob('"+encoded+"'),c=>c.charCodeAt(0))))";}
    static String html(Context context,JSONObject catalog)throws Exception{
        return html(context,catalog,new JSONObject());
    }
    static String html(Context context,JSONObject catalog,JSONObject selection)throws Exception{
        String code=read(context,"catalog-view.mjs").replace("export function mountCatalog","function mountCatalog");
        JSONObject payload=new JSONObject().put("catalog",catalog).put("selection",selection).put("assets",JourneyBookRenderer.assetsFor(context,catalog.getJSONArray("stickers")));
        return "<!doctype html><html lang='ko'><head><meta name='viewport' content='width=device-width,initial-scale=1'><meta http-equiv='Content-Security-Policy' content=\"default-src 'none'; img-src data:; script-src 'unsafe-inline'; style-src 'unsafe-inline'\"><style>button{cursor:pointer;font:inherit}*{box-sizing:border-box}button:focus-visible,summary:focus-visible{outline:3px solid #6ba7ff;outline-offset:3px}"+read(context,"catalog-view.css")+"</style></head><body class='vehicle-collection'><main id='collection'></main><script>"+code+"\nconst payload="+decode(encoded(payload))+";window.collection=mountCatalog({root:document.getElementById('collection'),catalog:payload.catalog,initialSelection:payload.selection,assetUrl:item=>'data:image/svg+xml;base64,'+payload.assets[item.id].base});document.body.dataset.ready='true';</script></body></html>";
    }
}
