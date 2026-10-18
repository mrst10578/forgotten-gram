package ir.forgottengram.archive;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class MainActivity extends Activity {
    private static final int BLUE = 0xFF2564BD;
    private static final String DEFAULT_TEMPLATE =
        "📚 {subject}\n🎬 جلسه: {session}\n📁 {destination}\n\n{caption}";
    private Client client;
    private int apiId;
    private String apiHash;
    private final Map<Long, TdApi.Chat> channels = new ConcurrentHashMap<>();
    private final List<TdApi.Message> messages = new ArrayList<>();
    private SharedPreferences prefs;
    private LinearLayout body;
    private String screen = "setup";
    private long sourceId, destinationId, cursor;
    private TdApi.Message selected;
    private long pendingMessageId, pendingSourceId, pendingSourceMessageId, pendingDestinationId;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("archive_settings", MODE_PRIVATE);
        destinationId = prefs.getLong("destination", 0L);
        setup();
    }

    private void page(String title, String sub) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xFFF5F8FF);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        int p = px(18);
        body.setPadding(p, p, p, p);
        scroll.addView(body);
        setContentView(scroll);
        label(title, 23, true);
        label(sub, 13, false);
    }

    private int px(int n) { return (int) (n * getResources().getDisplayMetrics().density + .5f); }
    private void label(String s, int size, boolean strong) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(strong ? 0xFF133D77 : 0xFF536C88);
        t.setGravity(Gravity.RIGHT);
        t.setPadding(0, px(6), 0, px(6));
        if (strong) t.setTypeface(null, 1);
        body.addView(t);
    }
    private EditText input(String hint, String value, int mode) {
        EditText f = new EditText(this);
        f.setHint(hint);
        f.setText(value);
        f.setTextSize(16);
        f.setInputType(mode);
        f.setTextDirection(View.TEXT_DIRECTION_RTL);
        body.addView(f);
        return f;
    }
    private void button(String title, Runnable action) {
        Button b = new Button(this);
        b.setText(title);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(BLUE));
        b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = px(8);
        body.addView(b, lp);
    }
    private void dialog(String title, String detail) {
        if (isFinishing()) return;
        new AlertDialog.Builder(this).setTitle(title).setMessage(detail)
                .setPositiveButton("باشه", null).show();
    }
    private void prompt(String title, boolean secret, java.util.function.Consumer<String> done) {
        EditText f = new EditText(this);
        f.setSingleLine();
        f.setPadding(px(20),px(12),px(20),px(12));
        if (secret) f.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        new AlertDialog.Builder(this).setTitle(title).setView(f).setCancelable(false)
                .setPositiveButton("ادامه", (d,w) -> done.accept(f.getText().toString().trim()))
                .setNegativeButton("انصراف", null).show();
    }

    private void setup() {
        screen = "setup";
        page("Forgotten Gram", "آرشیوساز شخصی تلگرام | بدون سرور و ربات");
        label("API ID و API Hash را فقط داخل این اپ وارد کن.", 14, false);
        EditText id = input("API ID", "", InputType.TYPE_CLASS_NUMBER);
        EditText hash = input("API Hash", "",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        button("ورود به تلگرام", () -> {
            try {
                apiId = Integer.parseInt(id.getText().toString().trim());
                apiHash = hash.getText().toString().trim();
                if (apiId <= 0 || apiHash.length() < 20) throw new Exception();
                startTdlib();
            } catch (Exception e) { dialog("ورودی نامعتبر", "API ID و API Hash را بررسی کن."); }
        });
        label("اطلاعات API در سورس یا تنظیمات اپ ذخیره نمی‌شوند. پایگاه داده نشست TDLib در فضای خصوصی برنامه قرار می‌گیرد.", 12, false);
    }
    private void startTdlib() {
        screen = "login";
        page("ورود به حساب", "در حال اتصال به تلگرام...");
        try {
            System.loadLibrary("tdjni");
            client = Client.create(this::update,
                e -> notifyError(String.valueOf(e)),
                e -> notifyError(String.valueOf(e)));
        } catch (Throwable t) {
            dialog("TDLib", "بارگذاری موتور تلگرام ناموفق بود: " + t);
            setup();
        }
    }
    private void notifyError(String m) { runOnUiThread(() -> dialog("تلگرام", m)); }
    private void send(TdApi.Function f, Client.ResultHandler cb) {
        if (client == null) return;
        client.send(f, r -> {
            if (r instanceof TdApi.Error) {
                TdApi.Error e = (TdApi.Error) r;
                notifyError(e.code + ": " + e.message);
            }
            if (cb != null) cb.onResult(r);
        });
    }
    private void update(TdApi.Object r) {
        if (r instanceof TdApi.UpdateAuthorizationState) {
            TdApi.AuthorizationState a = ((TdApi.UpdateAuthorizationState)r).authorizationState;
            runOnUiThread(() -> authorize(a));
        } else if (r instanceof TdApi.UpdateNewChat) {
            addChannel(((TdApi.UpdateNewChat)r).chat);
        } else if (r instanceof TdApi.UpdateChatTitle) {
            TdApi.UpdateChatTitle x = (TdApi.UpdateChatTitle)r;
            TdApi.Chat c = channels.get(x.chatId);
            if (c != null) c.title = x.title;
        } else if (r instanceof TdApi.UpdateMessageSendSucceeded) {
            TdApi.UpdateMessageSendSucceeded x = (TdApi.UpdateMessageSendSucceeded)r;
            runOnUiThread(() -> {
                if (pendingMessageId != 0 && x.oldMessageId == pendingMessageId
                        && x.message.chatId == pendingDestinationId) {
                    prefs.edit().putBoolean(key(pendingSourceId,pendingSourceMessageId),true).apply();
                    pendingMessageId = 0;
                    selected = null;
                    new AlertDialog.Builder(this).setTitle("آرشیو انجام شد")
                        .setMessage("پیام در کانال مقصد منتشر شد.")
                        .setPositiveButton("باشه",(d,w)->showMessages()).show();
                }
            });
        } else if (r instanceof TdApi.UpdateMessageSendFailed) {
            TdApi.UpdateMessageSendFailed x = (TdApi.UpdateMessageSendFailed)r;
            runOnUiThread(() -> {
                if (pendingMessageId != 0 && x.oldMessageId == pendingMessageId) {
                    pendingMessageId = 0;
                    dialog("ارسال ناموفق", x.error.message);
                }
            });
        }
    }
    private void authorize(TdApi.AuthorizationState a) {
        if (a instanceof TdApi.AuthorizationStateWaitTdlibParameters) {
            TdApi.SetTdlibParameters p = new TdApi.SetTdlibParameters();
            p.apiId = apiId;
            p.apiHash = apiHash;
            p.databaseDirectory = getFilesDir().getAbsolutePath()+"/td-db";
            p.filesDirectory = getFilesDir().getAbsolutePath()+"/td-files";
            p.useFileDatabase = true;
            p.useChatInfoDatabase = true;
            p.useMessageDatabase = true;
            p.useSecretChats = false;
            p.systemLanguageCode = "fa";
            p.deviceModel = android.os.Build.MODEL;
            p.systemVersion = android.os.Build.VERSION.RELEASE;
            p.applicationVersion = "0.1.0";
            send(p,null);
        } else if (a instanceof TdApi.AuthorizationStateWaitPhoneNumber) {
            prompt("شماره با کد کشور، مثل +98...", false, v ->
                send(new TdApi.SetAuthenticationPhoneNumber(v,null),null));
        } else if (a instanceof TdApi.AuthorizationStateWaitCode) {
            prompt("کد ورود تلگرام",false,v->send(new TdApi.CheckAuthenticationCode(v),null));
        } else if (a instanceof TdApi.AuthorizationStateWaitPassword) {
            prompt("رمز دومرحله‌ای",true,v->send(new TdApi.CheckAuthenticationPassword(v),null));
        } else if (a instanceof TdApi.AuthorizationStateWaitOtherDeviceConfirmation) {
            dialog("تأیید با دستگاه دیگر",
                ((TdApi.AuthorizationStateWaitOtherDeviceConfirmation)a).link);
        } else if (a instanceof TdApi.AuthorizationStateReady) {
            channelsPage();
            refreshChannels();
        } else if (a instanceof TdApi.AuthorizationStateClosed) {
            client = null;
            setup();
        } else {
            dialog("روش ورود", "مرحله: " + a.getClass().getSimpleName());
        }
    }
    private void addChannel(TdApi.Chat c) {
        if (c != null && c.type instanceof TdApi.ChatTypeSupergroup
            && ((TdApi.ChatTypeSupergroup)c.type).isChannel) {
            channels.put(c.id,c);
            runOnUiThread(() -> { if ("channels".equals(screen)) channelsPage(); });
        }
    }
    private void refreshChannels() {
        send(new TdApi.LoadChats(new TdApi.ChatListMain(),100),r -> readChats(new TdApi.ChatListMain()));
        send(new TdApi.LoadChats(new TdApi.ChatListArchive(),100),r -> readChats(new TdApi.ChatListArchive()));
        readChats(new TdApi.ChatListMain());
    }
    private void readChats(TdApi.ChatList list) {
        send(new TdApi.GetChats(list,200), r -> {
            if (r instanceof TdApi.Chats) for(long id : ((TdApi.Chats)r).chatIds)
                send(new TdApi.GetChat(id), c -> {
                    if(c instanceof TdApi.Chat) addChannel((TdApi.Chat)c);
                });
        });
    }
    private List<TdApi.Chat> sortedChannels() {
        List<TdApi.Chat> c = new ArrayList<>(channels.values());
        c.sort((a,b)->String.valueOf(a.title).compareToIgnoreCase(String.valueOf(b.title)));
        return c;
    }
    private String name(long chatId) {
        TdApi.Chat c = channels.get(chatId);
        return c == null ? "انتخاب نشده" : c.title;
    }
    private void channelsPage() {
        screen = "channels";
        page("کانال‌های تلگرام", "کانال مبدأ را انتخاب کن");
        button("تنظیم قالب کپشن", this::templatePage);
        button("به‌روزرسانی فهرست", this::refreshChannels);
        label("کانال‌ها: " + channels.size(),13,false);
        for(TdApi.Chat c: sortedChannels())
            button("📂 "+c.title, () -> {
                sourceId=c.id; cursor=0L; messages.clear();
                showMessages(); loadMessages();
            });
    }
    private void loadMessages() {
        final long expected=sourceId, from=cursor;
        send(new TdApi.GetChatHistory(expected,from,0,40,false),r -> {
            if (!(r instanceof TdApi.Messages)) return;
            runOnUiThread(()->{
                if (sourceId != expected) return;
                TdApi.Message[] loaded=((TdApi.Messages)r).messages;
                for(TdApi.Message m:loaded) {
                    if (m==null) continue;
                    boolean seen=false;
                    for(TdApi.Message old:messages) if(old.id==m.id) {seen=true;break;}
                    if(!seen) messages.add(m);
                }
                if(loaded.length>0) cursor=loaded[loaded.length-1].id;
                showMessages();
            });
        });
    }
    private String text(TdApi.FormattedText t) { return t==null || t.text==null ? "" : t.text; }
    private String caption(TdApi.Message m) {
        TdApi.MessageContent c=m.content;
        if(c instanceof TdApi.MessageVideo) return text(((TdApi.MessageVideo)c).caption);
        if(c instanceof TdApi.MessagePhoto) return text(((TdApi.MessagePhoto)c).caption);
        if(c instanceof TdApi.MessageDocument) return text(((TdApi.MessageDocument)c).caption);
        if(c instanceof TdApi.MessageAudio) return text(((TdApi.MessageAudio)c).caption);
        if(c instanceof TdApi.MessageAnimation) return text(((TdApi.MessageAnimation)c).caption);
        return "";
    }
    private boolean media(TdApi.Message m) {
        return m.content instanceof TdApi.MessageVideo || m.content instanceof TdApi.MessagePhoto
            || m.content instanceof TdApi.MessageDocument || m.content instanceof TdApi.MessageAudio
            || m.content instanceof TdApi.MessageAnimation;
    }
    private String labelFor(TdApi.Message m) {
        String kind=m.content instanceof TdApi.MessageVideo ? "🎬 ویدیو" :
            m.content instanceof TdApi.MessagePhoto ? "🖼 عکس" :
            m.content instanceof TdApi.MessageDocument ? "📄 فایل" :
            m.content instanceof TdApi.MessageAudio ? "🎵 صوت" :
            m.content instanceof TdApi.MessageAnimation ? "🎞 گیف" : "📝 پیام";
        String summary=caption(m);
        return kind+"  "+ DateFormat.getDateInstance().format(new Date(m.date*1000L))
            +"\n"+(summary.length()>90?summary.substring(0,90)+"…":summary);
    }
    private String key(long chat,long message) {return "archived."+chat+"."+message;}
    private void showMessages() {
        screen="messages";
        page("پیام‌های کانال", name(sourceId));
        button("بازگشت",this::channelsPage);
        if(messages.isEmpty()) label("در حال دریافت پیام‌ها...",14,false);
        for(TdApi.Message m:messages) {
            String prefix=prefs.getBoolean(key(sourceId,m.id),false) ? "✓ آرشیوشده\n" : "";
            button(prefix+labelFor(m), () -> inspect(m));
        }
        button("پیام‌های قدیمی‌تر",this::loadMessages);
    }
    private void inspect(TdApi.Message m) {
        if(!media(m)) { dialog("پیام متنی", "نسخه اول فقط رسانه‌ها را آرشیو می‌کند."); return; }
        long chat=sourceId;
        send(new TdApi.GetMessageProperties(chat,m.id), r -> runOnUiThread(()->{
            if(r instanceof TdApi.MessageProperties) {
                if(!((TdApi.MessageProperties)r).canBeCopied)
                    dialog("محتوای محافظت‌شده", "کپی این پیام توسط تلگرام مجاز نیست.");
                else { selected=m; preview(); }
            }
        }));
    }
    private void chooseTarget() {
        List<TdApi.Chat> list=sortedChannels();
        String[] titles=new String[list.size()];
        for(int i=0;i<list.size();i++) titles[i]=list.get(i).title;
        new AlertDialog.Builder(this).setTitle("کانال آرشیو مقصد")
            .setItems(titles,(d,i)->{
                destinationId=list.get(i).id;
                prefs.edit().putLong("destination",destinationId).apply();
                preview();
            }).show();
    }
    private void preview() {
        if(selected==null) return;
        screen="preview";
        page("پیش‌نمایش آرشیو",name(sourceId)+" ← "+name(destinationId));
        label(labelFor(selected),15,false);
        button("انتخاب کانال مقصد",this::chooseTarget);
        EditText subject=input("درس",prefs.getString("subject",""),InputType.TYPE_CLASS_TEXT);
        EditText session=input("شماره جلسه",prefs.getString("session",""),InputType.TYPE_CLASS_TEXT);
        EditText captionEdit=input("کپشن نهایی","",
            InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        captionEdit.setMinLines(5);
        Runnable updateCaption=()->{
            Map<String,String> vars=new HashMap<>();
            vars.put("source",name(sourceId));
            vars.put("destination",name(destinationId));
            vars.put("caption",caption(selected));
            vars.put("subject",subject.getText().toString());
            vars.put("session",session.getText().toString());
            vars.put("id",String.valueOf(selected.id));
            vars.put("date",DateFormat.getDateInstance().format(new Date(selected.date*1000L)));
            captionEdit.setText(TemplateEngine.render(
                prefs.getString("template",DEFAULT_TEMPLATE), vars));
        };
        updateCaption.run();
        button("بازسازی کپشن از قالب",updateCaption);
        button("ارسال به آرشیو",()->{
            if(destinationId==0 || destinationId==sourceId) {
                dialog("مقصد", "کانال مقصد را درست انتخاب کن.");return;
            }
            String result=captionEdit.getText().toString().trim();
            if(result.length()>1024){dialog("کپشن بلند", "کپشن را به حداکثر ۱۰۲۴ نویسه برسان.");return;}
            prefs.edit().putString("subject",subject.getText().toString())
                .putString("session",session.getText().toString()).apply();
            new AlertDialog.Builder(this).setTitle("تأیید نهایی")
                .setMessage("این پیام به «"+name(destinationId)+"» کپی شود؟")
                .setNegativeButton("انصراف",null)
                .setPositiveButton("ارسال",(d,w)->archive(result)).show();
        });
        button("بازگشت",this::showMessages);
    }
    private void archive(String caption) {
        if(selected==null || pendingMessageId!=0) {
            dialog("ارسال", "یک ارسال دیگر هنوز در جریان است.");return;
        }
        TdApi.MessageCopyOptions opts=new TdApi.MessageCopyOptions();
        opts.sendCopy=true;
        opts.replaceCaption=true;
        opts.newCaption=new TdApi.FormattedText(caption,new TdApi.TextEntity[0]);
        opts.newShowCaptionAboveMedia=false;
        TdApi.InputMessageForwarded copied=new TdApi.InputMessageForwarded();
        copied.fromChatId=sourceId;
        copied.messageId=selected.id;
        copied.copyOptions=opts;
        TdApi.SendMessage request=new TdApi.SendMessage();
        request.chatId=destinationId;
        request.inputMessageContent=copied;
        pendingSourceId=sourceId;
        pendingSourceMessageId=selected.id;
        pendingDestinationId=destinationId;
        send(request,r->runOnUiThread(()->{
            if(r instanceof TdApi.Message) {
                pendingMessageId=((TdApi.Message)r).id;
                Toast.makeText(this,"پیام در صف ارسال قرار گرفت",Toast.LENGTH_SHORT).show();
            }
        }));
    }
    private void templatePage() {
        screen="template";
        page("قالب کپشن", "متغیرها: {source} {destination} {caption} {subject} {session} {date} {id}");
        EditText e=input("قالب",prefs.getString("template",DEFAULT_TEMPLATE),
            InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        e.setMinLines(6);
        button("ذخیره قالب",()->{
            prefs.edit().putString("template",e.getText().toString()).apply();
            channelsPage();
        });
        button("بازگردانی قالب پیش‌فرض",()->e.setText(DEFAULT_TEMPLATE));
        button("بازگشت",this::channelsPage);
    }
    @Override public void onBackPressed() {
        if("preview".equals(screen)) showMessages();
        else if("messages".equals(screen)||"template".equals(screen)) channelsPage();
        else super.onBackPressed();
    }
    @Override protected void onDestroy() {
        if(client!=null && isFinishing()) client.send(new TdApi.Close(),r->{});
        super.onDestroy();
    }
}
