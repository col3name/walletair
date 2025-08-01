package me.everything.android.ui.overscroll;

/**
 * A callback-listener enabling over-scroll effect clients to subscribe to <b>real-time</b> updates
 * of over-scrolling intensity, provided as the view-translation offset from pre-scroll position.
 *
 * @see IOverScrollStateListener
 */
public interface IOverScrollUpdateListener {

    /**
     * The invoked callback.
     *
     * @param decor         The associated over-scroll 'decorator'.
     * @param isTouchActive Is view being touched or bouncing.
     * @param state         One of: {@link IOverScrollState#STATE_IDLE}, {@link IOverScrollState#STATE_DRAG_START_SIDE},
     *                      {@link IOverScrollState#STATE_DRAG_START_SIDE} or {@link IOverScrollState#STATE_BOUNCE_BACK}.
     * @param offset        The currently visible offset created due to over-scroll.
     * @param velocity      Scroll velocity.
     */
    void onOverScrollUpdate(IOverScrollDecor decor, boolean isTouchActive, int state, float offset, float velocity);
}
