package com.kabigonis.sorairo

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors

private data class WorldCity(val name: String, val zone: String) {
    fun id() = ZoneId.of(zone)
}
private val worldCities = listOf(
    WorldCity("Abu Dhabi","Asia/Dubai"), WorldCity("Albuquerque","America/Denver"),
    WorldCity("Alexandria","Africa/Cairo"), WorldCity("Atlanta","America/New_York"),
    WorldCity("Austin","America/Chicago"), WorldCity("Bangalore","Asia/Kolkata"),
    WorldCity("Bangkok","Asia/Bangkok"), WorldCity("Beijing","Asia/Shanghai"),
    WorldCity("Berlin","Europe/Berlin"), WorldCity("Boston","America/New_York"),
    WorldCity("Buenos Aires","America/Argentina/Buenos_Aires"), WorldCity("Cairo","Africa/Cairo"),
    WorldCity("Cape Town","Africa/Johannesburg"), WorldCity("Chicago","America/Chicago"),
    WorldCity("Delhi","Asia/Kolkata"), WorldCity("Dubai","Asia/Dubai"),
    WorldCity("Dublin","Europe/Dublin"), WorldCity("Helsinki","Europe/Helsinki"),
    WorldCity("Hong Kong","Asia/Hong_Kong"), WorldCity("Istanbul","Europe/Istanbul"),
    WorldCity("Jakarta","Asia/Jakarta"), WorldCity("Johannesburg","Africa/Johannesburg"),
    WorldCity("Karachi","Asia/Karachi"), WorldCity("Kolkata","Asia/Kolkata"),
    WorldCity("London","Europe/London"), WorldCity("Los Angeles","America/Los_Angeles"),
    WorldCity("Madrid","Europe/Madrid"), WorldCity("Manila","Asia/Manila"),
    WorldCity("Melbourne","Australia/Melbourne"), WorldCity("Mexico City","America/Mexico_City"),
    WorldCity("Moscow","Europe/Moscow"), WorldCity("Mumbai","Asia/Kolkata"),
    WorldCity("Nairobi","Africa/Nairobi"), WorldCity("New York","America/New_York"),
    WorldCity("Paris","Europe/Paris"), WorldCity("Perth","Australia/Perth"),
    WorldCity("Riyadh","Asia/Riyadh"), WorldCity("Rome","Europe/Rome"),
    WorldCity("Seoul","Asia/Seoul"), WorldCity("Shanghai","Asia/Shanghai"),
    WorldCity("Singapore","Asia/Singapore"), WorldCity("Sydney","Australia/Sydney"),
    WorldCity("Tokyo","Asia/Tokyo"), WorldCity("Toronto","America/Toronto"),
    WorldCity("Vancouver","America/Vancouver"), WorldCity("Vienna","Europe/Vienna"),
    WorldCity("Zurich","Europe/Zurich")
)
private val worldWeatherExecutor = Executors.newFixedThreadPool(2)
private val worldWeatherCache = HashMap<String, Pair<Long,String>>()
private fun fetchWorldWeather(city: WorldCity, done: (String) -> Unit) {
    synchronized(worldWeatherCache) {
        val cached = worldWeatherCache[city.zone]
        if (cached != null && System.currentTimeMillis() - cached.first < 900_000L) { done(cached.second); return }
    }
    worldWeatherExecutor.execute {
        val value = try {
            val name = URLEncoder.encode(city.name, "UTF-8")
            val c = URL("https://wttr.in/$name?format=j1").openConnection() as HttpURLConnection
            c.connectTimeout = 4500; c.readTimeout = 4500
            val json = c.inputStream.bufferedReader().use { it.readText() }; c.disconnect()
            val now = JSONObject(json).getJSONArray("current_condition").getJSONObject(0)
            val desc = now.getJSONArray("weatherDesc").getJSONObject(0).optString("value")
            val glyph = when {
                desc.contains("rain",true) -> "☂"
                desc.contains("cloud",true) -> "☁"
                desc.contains("snow",true) -> "❄"
                desc.contains("sun",true) || desc.contains("clear",true) -> "☀"
                else -> "·"
            }
            "${now.optString("temp_F")}°F $glyph"
        } catch (_: Exception) { "—" }
        synchronized(worldWeatherCache) { worldWeatherCache[city.zone] = System.currentTimeMillis() to value }
        done(value)
    }
}
private fun WorldCity.time() = DateTimeFormatter.ofPattern("h:mm",Locale.US).withZone(id()).format(Instant.now())
private fun WorldCity.ampm() = DateTimeFormatter.ofPattern("a",Locale.US).withZone(id()).format(Instant.now())
private fun WorldCity.date() = DateTimeFormatter.ofPattern("EEE MMM d",Locale.US).withZone(id()).format(Instant.now())
private fun WorldCity.offset(): String {
    val sec = id().rules.getOffset(Instant.now()).totalSeconds
    val h = kotlin.math.abs(sec) / 3600; val m = kotlin.math.abs(sec) % 3600 / 60
    return "UTC${if (sec < 0) "−" else "+"}$h" + if (m == 0) "" else ":${m.toString().padStart(2,'0')}"
}
private class WorldClockTrack(context: Context, private val city: WorldCity, accent: Int, muted: Int) : View(context) {
    private val line = Paint(3).apply { color = muted; strokeWidth = 3f * resources.displayMetrics.density; strokeCap = Paint.Cap.ROUND }
    private val dot = Paint(3).apply { color = accent }
    private var pos = 0f
    init { pos = fraction() }
    private fun fraction(): Float { val t = ZonedDateTime.now(city.id()); return (t.hour * 60 + t.minute) / 1440f }
    override fun onDraw(c: Canvas) {
        val pad = 2f * resources.displayMetrics.density; val y = height / 2f
        c.drawLine(pad,y,width-pad,y,line); c.drawCircle(pad+(width-2*pad)*pos,y,6f*resources.displayMetrics.density,dot)
    }
    fun refresh() { ValueAnimator.ofFloat(pos,fraction()).apply { duration=380; interpolator=DecelerateInterpolator(); addUpdateListener { pos=it.animatedValue as Float; invalidate() }; start() } }
}
internal class WorldClockGlobeView(context: Context, private val tint: Int) : View(context) {
    private val paint = Paint(3).apply { color=tint; style=Paint.Style.STROKE; strokeWidth=1.7f*resources.displayMetrics.density }
    override fun onDraw(c: Canvas) {
        val x=width/2f; val y=height/2f; val r=minOf(width,height)*.39f
        c.drawCircle(x,y,r,paint); c.drawOval(RectF(x-r*.42f,y-r,x+r*.42f,y+r),paint)
        c.drawOval(RectF(x-r,y-r*.42f,x+r,y+r*.42f),paint); c.drawLine(x-r,y,x+r,y,paint)
    }
}
internal fun MainActivity.showWorldClock() {
    val p=pal; val bg=p.cBg or (0xFF shl 24)
    val defaults=listOf("Los Angeles","Paris","Tokyo")
    var cities=prefs.getString("world_clock_cities",null)?.split('|')?.mapNotNull { n->worldCities.firstOrNull{it.name==n} }?.distinctBy{it.zone}?.toMutableList()
        ?: defaults.mapNotNull{n->worldCities.firstOrNull{it.name==n}}.toMutableList()
    val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(16),dp(14),dp(16),dp(12)); background=box(bg,p.cBlue) }
    val scroll=ScrollView(this).apply { isFillViewport=true }
    val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
    scroll.addView(body); root.addView(scroll,LinearLayout.LayoutParams(-1,dp(590)))
    val here=WorldCity("Here",java.util.TimeZone.getDefault().id)
    val local=txt(11f,p.cDim,"It's ${here.time()} ${here.ampm()} here").apply{gravity=Gravity.CENTER}
    body.addView(local,LinearLayout.LayoutParams(-1,dp(24)))
    val heading=LinearLayout(this).apply{gravity=Gravity.CENTER;orientation=LinearLayout.HORIZONTAL}
    heading.addView(WorldClockGlobeView(this,p.cDim),LinearLayout.LayoutParams(dp(34),dp(34)).apply{marginEnd=dp(10)})
    heading.addView(txt(19f,p.cFg,"World Clock").apply{setTypeface(typeface,android.graphics.Typeface.BOLD)})
    body.addView(heading,LinearLayout.LayoutParams(-1,dp(42)))
    val cards=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
    body.addView(cards,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(10)})
    val search=EditText(this).apply{
        hint="Search cities…";textSize=13f*uiScale;typeface=font;setTextColor(p.cFg);setHintTextColor(p.cDim)
        setSingleLine(true);setPadding(dp(12),0,dp(12),0);background=box(p.cSel,p.cDim and 0x00FFFFFF or (0x55 shl 24))
    }
    body.addView(search,LinearLayout.LayoutParams(-1,dp(42)).apply{topMargin=dp(12)})
    val suggestions=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};body.addView(suggestions)
    val weatherViews=HashMap<String,TextView>();val tracks=HashMap<String,WorldClockTrack>()
    var updateSuggestions:(String)->Unit={}
    fun save(){prefs.edit().putString("world_clock_cities",cities.joinToString("|"){it.name}).apply()}
    fun renderCities(){
        cards.removeAllViews();weatherViews.clear();tracks.clear()
        cities.forEachIndexed{index,city->
            val row=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(9),dp(12),dp(8));background=box(p.cSel,Color.TRANSPARENT)}
            val top=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
            val left=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;layoutParams=LinearLayout.LayoutParams(0,-2,1f)}
            left.addView(txt(14f,p.cFg,city.name).apply{setTypeface(typeface,android.graphics.Typeface.BOLD)})
            left.addView(txt(10f,p.cDim,city.date()))
            top.addView(left)
            val weather=txt(10f,p.cDim,"…").apply{gravity=Gravity.END or Gravity.CENTER_VERTICAL;setPadding(0,0,dp(8),0)}
            weatherViews[city.zone]=weather;top.addView(weather)
            val right=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.END}
            right.addView(txt(19f,p.cFg,city.time()).apply{setTypeface(typeface,android.graphics.Typeface.BOLD)})
            right.addView(txt(9f,p.cDim,city.ampm()));top.addView(right);row.addView(top)
            val track=WorldClockTrack(this,city,p.cAccent,p.cDim and 0x00FFFFFF or (0x55 shl 24))
            tracks[city.zone]=track;row.addView(track,LinearLayout.LayoutParams(-1,dp(15)).apply{topMargin=dp(5)})
            row.setOnLongClickListener{if(cities.size>1){cities.removeAt(index);save();renderCities();updateSuggestions(search.text.toString());true}else false}
            cards.addView(row,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(7)})
            fetchWorldWeather(city){v->handler.post{weatherViews[city.zone]?.text=v}}
        }
    }
    fun renderSuggestions(q:String){
        suggestions.removeAllViews()
        val matches=if(q.isBlank()) worldCities.filter{it.name in listOf("Abu Dhabi","Albuquerque","Alexandria","Atlanta","Austin","Bangalore")}
            else worldCities.filter{it.name.contains(q,true)||it.zone.contains(q,true)}.filterNot{candidate->cities.any{it.zone==candidate.zone}}.take(8)
        matches.forEach{city->
            val item=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(10),dp(8),dp(10),dp(8))}
            item.addView(txt(12f,p.cFg,city.name),LinearLayout.LayoutParams(0,-2,1f))
            item.addView(txt(10f,p.cDim,"${city.zone}  ${city.offset()}"))
            item.setOnClickListener{if(cities.none{it.zone==city.zone}){cities.add(city);save();renderCities()};search.setText("");search.clearFocus()}
            suggestions.addView(item)
        }
    }
    updateSuggestions=::renderSuggestions
    search.addTextChangedListener(object:TextWatcher{
        override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int)=Unit
        override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int)=renderSuggestions(s?.toString().orEmpty())
        override fun afterTextChanged(s:Editable?)=Unit
    })
    val foot=txt(10f,p.cDim,"Tap a city to add · Long-press a clock to remove").apply{gravity=Gravity.CENTER;setPadding(0,dp(10),0,dp(2))}
    body.addView(foot)
    renderCities();renderSuggestions("")
    var ticker:Runnable?=null
    val dialog=showBarDialog(BarKind.WORLDCLOCK,root,440){ticker?.let{handler.removeCallbacks(it)}}
    ticker=object:Runnable{
        override fun run(){
            if(!dialog.isShowing)return
            local.text="It's ${here.time()} ${here.ampm()} here"
            cities.forEachIndexed{i,city->
                val row=cards.getChildAt(i) as? LinearLayout?:return@forEachIndexed
                val top=row.getChildAt(0) as? LinearLayout?:return@forEachIndexed
                val left=top.getChildAt(0) as? LinearLayout?:return@forEachIndexed
                (left.getChildAt(1) as? TextView)?.text=city.date()
                val right=top.getChildAt(2) as? LinearLayout?:return@forEachIndexed
                (right.getChildAt(0) as? TextView)?.text=city.time()
                (right.getChildAt(1) as? TextView)?.text=city.ampm()
                tracks[city.zone]?.refresh()
            }
            handler.postDelayed(this,30_000L)
        }
    }
    handler.postDelayed(ticker!!,30_000L)
}