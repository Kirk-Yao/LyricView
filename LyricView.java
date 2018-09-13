import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.os.CountDownTimer;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.Scroller;

import java.util.List;

/**
 * Created by ky on 2018/5/8.
 * 歌词控件
 */

public class LyricView extends View {

    private static final String TAG = "LyricView";

    private static final String DEFAULT_TEXT = "*暂无歌词*";
    private static final String LOADING_LRC_TEXT = "正在加载歌词…";

    // 高亮歌词
    private static final int DEFAULT_COLOR_FOR_HIGHLIGHT_LRC = 0xffffffff;
    // 高亮歌词上下一句
    private static final int DEFAULT_COLOR_BESIDE_HIGHLIGHT_LRC = 0x50ffffff;
    // 其他歌词
    private static final int DEFAULT_COLOR_FOR_OTHER_LRC = 0x30ffffff;
    private static final int DEFAULT_COLOR_FOR_PROGRESS =0x55ffffff;
    private static final int COLOR_FOR_TIME_LINE = 0xff5a5a5a;
    // 自动滚动每一行歌词的持续时长
    private static final int DURATION_SCROLL_LRC = 500;
    // 歌词最大宽度，单位px
    private static final int LRC_MAX_WIDTH = 680;
    // 延迟消失indicator的时间，ms
    private static final int DELAY_HIDE_DURATION = 3000;

    // 是否正在拖动歌词
    private boolean isDragingLrc = false;
    // ACTION_DOWN是否落在play按钮上
    private boolean isClickPlay;
    // 是否是一次点击事件，用于确认点击事件，切换歌词显示/关闭
    private boolean isClickEvent = false;
    // 是否需要画指示线，进度和播放按钮
    private boolean needDrawIndicator = false;
    // 是否正在显示指示线，进度和播放按钮
    private boolean isShowingIndicator = false;
    // 是否正在加载歌词
    private boolean isLoadingLrc = false;

    private List<LrcRow> lrcRowList;
    // 实现歌词垂直方向滚动的辅助类
    private Scroller scroller;
    private int curLine;
    // 水平滚动歌词的x坐标
    private float horizonScrollTextX = 0;

    private OnPlayClickListener onPlayClickListener;
    private OnViewClickListener onViewClickListener;
    // 用户横向滚动的动画
    private ValueAnimator animator;
    // 用于实现横向滚动
    private CountDownTimer timer;

    // 用于计算高亮歌词的进度
    private CountDownTimer percentageTimer;
    // 高亮歌词的播放进度
    private float finishPercentage;


    private float downY;
    private float lastY;
    private int touchSlop;

    private Paint highlightPaint;
    private Paint normalTextPaint;
    private Paint timelinePaint;
    private Paint progressPaint;
    private Bitmap playBitmap;

    private int highlightColor = DEFAULT_COLOR_FOR_HIGHLIGHT_LRC;
    private float highlightTextSize = 32;
    private int besideHighloghtColor = DEFAULT_COLOR_BESIDE_HIGHLIGHT_LRC;
    private float besideHighlightTextSize = 28;
    private int normalTextColor = DEFAULT_COLOR_FOR_OTHER_LRC;
    private float normalTextSize = 27;
    private int progressColor = DEFAULT_COLOR_FOR_PROGRESS;
    private float progressTextSize = 16;
    // 用于高亮歌词的渐变色
    private int[] colors = {highlightColor, normalTextColor};

    // 垂直方向上的padding
    private int padding = 25;
    // 每行歌词的高度
    private float eachLineHeight = normalTextSize + padding;

    // 用于控制indicator的显示逻辑
    Runnable hideIndicatorRunnable = new Runnable() {
        @Override
        public void run() {
            isShowingIndicator = false;
            if (hasLrc()) {
                needDrawIndicator = false;
                smoothScrollTo(getYHeight(curLine));
            }
        }
    };

    public LyricView(Context context) {
        super(context);
        init();
    }

    public LyricView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LyricView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        scroller = new Scroller(getContext());

        highlightPaint = new Paint();
        highlightPaint.setColor(highlightColor);
        highlightPaint.setTextSize(highlightTextSize);
        highlightPaint.setAntiAlias(true);

        normalTextPaint = new Paint();
        normalTextPaint.setColor(normalTextColor);
        normalTextPaint.setTextSize(normalTextSize);
        normalTextPaint.setAntiAlias(true);

        timelinePaint = new Paint();
        timelinePaint.setColor(COLOR_FOR_TIME_LINE);
        timelinePaint.setTextSize(5);

        progressPaint = new Paint();
        progressPaint.setTextSize(progressTextSize);
        progressPaint.setAntiAlias(true);
        progressPaint.setColor(progressColor);

        playBitmap = ((BitmapDrawable)getResources().getDrawable(R.drawable.play_src_btn)).getBitmap();
        touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inDensity = 30;
        options.inTargetDensity = 30;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (isLoadingLrc) {
            drawHintText(canvas, LOADING_LRC_TEXT);
            return;
        }

        if (!hasLrc()) {
            drawHintText(canvas, DEFAULT_TEXT);
            return;
        }

        if (needDrawIndicator) {
            drawIndicator(canvas);
        }

        float y = getHeight() / 2 + 10;
        for (int i = 0; i < lrcRowList.size(); i++) {
            String lrc = getLrc(i);
            if (i == curLine) {
                drawHighlightText(canvas, lrc, y);
            } else {
                drawNormalText(canvas, i, y);
            }
            // 计算得到y坐标
            y = y + eachLineHeight;
        }
    }

    /**
     * 当正在加载或者暂无歌词时，绘制提示词
     */
    private void drawHintText(Canvas canvas, String text) {
        if (TextUtils.isEmpty(text)) {
            return;
        }
        float textWidth = normalTextPaint.measureText(text);
        float textX = (getWidth() - textWidth) / 2;
        canvas.drawText(text, textX, getHeight() / 2, normalTextPaint);
    }

    private void drawIndicator(Canvas canvas) {
        if (!hasLrc()) {
            return;
        }
        isShowingIndicator = true;
        // 因为会调用scroll滚动，所以需要加上getScrollY()
        float y = getHeight() / 2 + getScrollY() - 5;
        float x = getWidth();

        canvas.drawLine(105, y, x - 72, y, timelinePaint);
        canvas.drawBitmap(playBitmap, x-57, y - playBitmap.getHeight()/2, null);

        String curProgress = lrcRowList.get(curLine).getTimeStr().substring(0, 5);
        Paint.FontMetricsInt fontMetricsInt = progressPaint.getFontMetricsInt();
        // 文字所占高度
        int fontHeight = fontMetricsInt.bottom - fontMetricsInt.top;
        // 文字垂直方向中心距离baseline的距离
        int offY = fontHeight / 2 - fontMetricsInt.bottom;
        float baselineY = y + offY;
        canvas.drawText(curProgress, 60, baselineY, progressPaint);
    }

    private void drawHighlightText(Canvas canvas, String text, float y) {
        if (text.isEmpty()) {
            return;
        }

        canvas.save();
        float textWidth = highlightPaint.measureText(text);
        // 默认为居中显示
        float x = (getWidth() - textWidth) / 2;
        if (textWidth > LRC_MAX_WIDTH) {
            // 歌词宽度大于控件宽度，动态设置歌词的起始x坐标，实现滚动显示
            x = horizonScrollTextX;
            RectF rect = new RectF(getLrcStartX(), y - highlightTextSize,
                    getLrcStartX() + LRC_MAX_WIDTH, y + highlightTextSize);
            canvas.clipRect(rect);
        }

        // 设置shader，用于渐变色显示
        float[] positions = new float[] {finishPercentage, finishPercentage + 0.1f};
        highlightPaint.setShader(new LinearGradient(x, y, x+textWidth, y,
                colors, positions, Shader.TileMode.CLAMP));

        canvas.drawText(text, x, y, highlightPaint);
        canvas.restore();
    }

    private void drawNormalText(Canvas canvas, int lineNo, float y) {
        String text = getLrc(lineNo);
        if (text.isEmpty()) {
            return;
        }

        // 因为高亮歌词上下一行的字号和透明度，与其他位置的普通歌词不同
        if (lineNo == curLine - 1 || lineNo == curLine + 1) {
            normalTextPaint.setColor(besideHighloghtColor);
            normalTextPaint.setTextSize(besideHighlightTextSize);
        } else {
            normalTextPaint.setColor(normalTextColor);
            normalTextPaint.setTextSize(normalTextSize);
        }

        canvas.save();
        float textWidth = normalTextPaint.measureText(text);
        float x = (getWidth() - textWidth) / 2;
        if (textWidth > LRC_MAX_WIDTH) {
            // 如果歌词宽度大于控件宽度，则居左显示
            x = getLrcStartX();
            RectF rect = new RectF(getLrcStartX(), y - normalTextSize,
                    getLrcStartX() + LRC_MAX_WIDTH, y + normalTextSize);
            canvas.clipRect(rect);
        }
        canvas.drawText(text, x, y, normalTextPaint);
        canvas.restore();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                actionDown(event);
                break;
            case MotionEvent.ACTION_MOVE:
                if (!hasLrc()) {
                    return false;
                }

                if (!isDragingLrc) {
                    if (Math.abs(event.getY() - downY) > touchSlop) {
                        isDragingLrc = true;
//                        stopHorizontalScroll();
                        stopHorizontalScrollWithTimer();
                        scroller.forceFinished(true);
                        lastY = event.getY();
                    }
                }

                if (isDragingLrc) {
                    isClickEvent = false;
                    float deltaY = event.getY() - lastY;
                    if ((getScrollY() - deltaY) < -eachLineHeight) {
                        // 处理上滑边界，如果已经滑动至顶端，则限制其继续上滑
                        deltaY = deltaY > 0 ? 0 : deltaY;
                    } else if ((getScrollY() - deltaY) > lrcRowList.size() * eachLineHeight) {
                        // 处理下滑边界
                        deltaY = deltaY < 0 ? 0 : deltaY;
                    }

                    scrollBy(getScrollX(), -(int)deltaY);
                    curLine = calculateLineNo();
                    lastY = event.getY();
                    return true;
                }
                lastY = event.getY();
                break;
            case MotionEvent.ACTION_UP:
                actionUp(event);
                break;
            case MotionEvent.ACTION_CANCEL:
                // 如果出现侧向轻微滑动，并抬起手指的情况，此时并不会走ACTION_UP的回调
                // 而是会走ACTION_CANCEL.
                isDragingLrc = false;
                Log.d(TAG, "cancel event!");
                break;
            default:
                break;
        }
        return true;
    }

    private void actionDown(MotionEvent event) {
        isClickEvent = true;
        if (!hasLrc()) {
            return;
        }

        removeCallbacks(hideIndicatorRunnable);
        downY = event.getY();
        needDrawIndicator = true;
        isClickPlay = isClickPlayBtn(event);
    }

    private void actionUp(MotionEvent event) {
        if (isClickEvent && !isClickPlay) {
            needDrawIndicator = false;
            isDragingLrc = false;
            isShowingIndicator = false;
            if (onViewClickListener != null) {
                onViewClickListener.onClick();
            }
        }

        if (!hasLrc()) {
            return;
        }

        isDragingLrc = false;
        // 设置3s后隐藏indicator
        postHideIndicator();

        // 只有当正在显示播放按钮，且点击事件落在其上时，才响应
        if (isClickPlay && isClickPlayBtn(event) && isShowingIndicator) {
            // 如果点击播放，则立即刷新界面
            needDrawIndicator = false;
            isShowingIndicator = false;
            invalidate();
            if (onPlayClickListener != null) {
                // 避免外部调用setProgress方法，将curLine重置，此处再主动计算一次curLine
                curLine = calculateLineNo();
                Log.d(TAG, "onPlayClick() -> " + getLrc(curLine));
                int progress = lrcRowList.get(curLine).getTime() / 1000;
                onPlayClickListener.onClick(progress);
                isClickPlay = false;
            }
        }
    }

    private void seekProgress(int progress, boolean seekbarByUser) {
        int lineNum = getLineNum(progress);
        if (lineNum != curLine) {
            curLine = lineNum;

            if (needDrawIndicator && !seekbarByUser) {
                Log.d(TAG, "showing indicator");
                postHideIndicator();
            } else {

                if (seekbarByUser) {
                    hideIndicator();
                    forceScrollTo(getScrollX(), getYHeight(curLine));
                } else {
                    smoothScrollTo(getYHeight(curLine));
                }
                checkNeedHorizScroll();
                calculateProgress(lrcRowList.get(curLine).getTotalTime());
            }
        }
    }

    private void postHideIndicator() {
        Log.d(TAG, "postHideIndicator()");
        removeCallbacks(hideIndicatorRunnable);
        postDelayed(hideIndicatorRunnable, DELAY_HIDE_DURATION);
    }

    @Override
    public void computeScroll() {
        super.computeScroll();

        if (scroller.computeScrollOffset()) {
            int oldY = getScrollY();
            int curY = scroller.getCurrY();
            if (oldY != curY && !isDragingLrc) {
                scrollTo(getScrollX(), curY);
            }
            invalidate();
        }
    }

    private void smoothScrollTo(int targetY) {
        if (!scroller.isFinished()) {
            scroller.forceFinished(true);
        }

        int oldScrollY = getScrollY();
        int deltaY = targetY - oldScrollY;
        scroller.startScroll(getScrollX(), oldScrollY, 0, deltaY, DURATION_SCROLL_LRC);
        invalidate();
    }

    private void forceScrollTo(int targetX, int targetY) {
        if (!scroller.isFinished()) {
            scroller.forceFinished(true);
        }

        scrollTo(targetX, targetY);
    }

    /**
     * 计算curLine已经播放过的进度
     * @param duration 当前行歌词的时长
     */
    private void calculateProgress(final long duration) {
        if (percentageTimer != null) {
            percentageTimer.cancel();
            percentageTimer = null;
        }
        percentageTimer = new CountDownTimer(duration, 100) {
            @Override
            public void onTick(long millisUntilFinished) {
                finishPercentage = 1 - ((float)millisUntilFinished) / duration;
                // 避免最后一个字因为计算问题，显示不完整
                if (finishPercentage > 0.9) {
                    finishPercentage = 1;
                }
                invalidate();
            }

            @Override
            public void onFinish() {

            }
        };
        percentageTimer.start();
    }

    private void checkNeedHorizScroll() {
        String text = getLrc(curLine);
        float textWidth = highlightPaint.measureText(text);
        if (textWidth > LRC_MAX_WIDTH) {
            startHorizontalScrollWithTimer(LRC_MAX_WIDTH + getLrcStartX() - textWidth,
                    lrcRowList.get(curLine).getTotalTime());
//            startHorizontalScroll(LRC_MAX_WIDTH + getLrcStartX() - textWidth,
//                    lrcRowList.get(curLine).getTotalTime());
        }
    }

    /**
     * 以动画的方式，不停改变歌词的起始x坐标，重绘，达到水平滚动的目的
     * Notice： 动画的onAnimationUpdate()方法偶尔会出现只调用两三次的情况，导致失效！原因不明
     */
    private void startHorizontalScroll(float endX, long duration) {
        if (animator == null) {
            animator = ValueAnimator.ofFloat(getLrcStartX(), endX);
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    Log.d(TAG, "anim update:" + animation.getAnimatedValue());
                    horizonScrollTextX = (float) animation.getAnimatedValue();
                    invalidate();
                }
            });
        } else {
            horizonScrollTextX = getLrcStartX();
            animator.cancel();
            animator.setFloatValues(getLrcStartX(), endX);
        }

        animator.setDuration(duration);
        animator.start();
    }

    private void stopHorizontalScroll() {
        if (animator != null) {
            animator.cancel();
        }
        horizonScrollTextX = 0;
    }

    /**
     * 以定时器的方式实现歌词的横向滚动，因为动画的方式偶尔失效
     */
    private void startHorizontalScrollWithTimer(final float endX, final long duration) {
        stopHorizontalScrollWithTimer();

        timer = new CountDownTimer(duration, 100) {
            @Override
            public void onTick(long millisUntilFinished) {
                float finishPercentage = 1 - ((float)millisUntilFinished) / duration;
                // 避免最后一个字因为计算问题，显示不完整
                if (finishPercentage > 0.9) {
                    finishPercentage = 1;
                }

                horizonScrollTextX = endX * finishPercentage;
                invalidate();
            }

            @Override
            public void onFinish() {

            }
        };
        timer.start();
    }

    private void stopHorizontalScrollWithTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    private void hideIndicator() {
        needDrawIndicator = false;
        isShowingIndicator = false;
        invalidate();
    }

    /**
     * 判断点击事件区域是否落在播放按键上
     */
    private boolean isClickPlayBtn(MotionEvent event) {
        if (event == null) {
            return false;
        }

        // 用于增大点击区域
        int spaceHolder = 20;
        // x y分别是draw play按钮时的坐标
        float x = getWidth() - 77;
        float y = getHeight() / 2 - playBitmap.getHeight()/2;
        return event.getX() > x && event.getY() > y - spaceHolder &&
                event.getY() < (y + playBitmap.getHeight() + spaceHolder);
    }

    /**
     * 根据传入的进度，计算对应的行号
     * @param progress: 传入进度，单位为秒
     */
    private int  getLineNum(int progress) {
        if (!hasLrc()) {
            return 0;
        }

        for (int i = lrcRowList.size() - 1; i >= 0; i--) {
            if (lrcRowList.get(i).getTime() / 1000 <= progress) {
                return i;
            }
        }
        return 0;
    }

    /**
     * 根据在垂直方向上的滚动距离，计算当前的高亮行号
     */
    private int calculateLineNo() {
        if (!hasLrc()) {
            return 0;
        }

        int curLineNum = (int) (getScrollY() / eachLineHeight);
        curLineNum = Math.max(curLineNum, 0);
        curLineNum = Math.min(curLineNum, lrcRowList.size() - 1);
        return curLineNum;
    }

    private int getYHeight(int lineNum) {
        return (int)(lineNum * eachLineHeight);
    }

    /**
     * 计算当歌词宽度大于LRC_MAX_WIDTH时，歌词的x起始坐标
     */
    private int getLrcStartX() {
        return (getWidth() - LRC_MAX_WIDTH) / 2;
    }

    private String getLrc(int pos) {
        if (!hasLrc() || pos < 0 || pos >= lrcRowList.size()) {
            return "";
        }
        return lrcRowList.get(pos).getContent();
    }

    private boolean hasLrc() {
        return lrcRowList != null && !lrcRowList.isEmpty();
    }


    // ------对外提供的方法--------

    public void setLrcRows(List<LrcRow> lrcRows) {
        reset();
        isLoadingLrc = false;
        this.lrcRowList = lrcRows;
        invalidate();
    }

    /**
     * 设置当前进度
     * @param progress: 当前进度，单位为秒.
     * @param seekbarByUser: 是否由用户拖动seekbar导致
     */
    public void setProgress(int progress, boolean seekbarByUser) {
        if (!hasLrc()) {
            return;
        }

        seekProgress(progress, seekbarByUser);
    }

    public void reset() {
        Log.d(TAG, "reset()");
        forceScrollTo(getScrollX(), 0);
        lrcRowList = null;
        isLoadingLrc = false;
        curLine = 0;
        needDrawIndicator = false;
        isShowingIndicator = false;
        isDragingLrc = false;
        removeCallbacks(hideIndicatorRunnable);
        invalidate();
    }

    public void showLoading() {
        reset();
        isLoadingLrc = true;
    }

    public void setOnPlayClickListener(OnPlayClickListener onPlayClickListener) {
        this.onPlayClickListener = onPlayClickListener;
    }

    public void setOnViewClickListener(OnViewClickListener onViewClickListener) {
        this.onViewClickListener = onViewClickListener;
    }

    /**
     * 用于监听播放按钮是否被点击
     */
    public interface OnPlayClickListener {
        // progress为点击播放时，选中的歌词对应的时间，单位为秒
        void onClick(int progress);
    }

    /**
     * 用于监听该view是否被点击
     */
    public interface OnViewClickListener {
        void onClick();
    }
}
