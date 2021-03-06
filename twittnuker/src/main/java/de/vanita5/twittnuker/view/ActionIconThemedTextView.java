/*
 * Twittnuker - Twitter client for Android
 *
 * Copyright (C) 2013-2015 vanita5 <mail@vanita5.de>
 *
 * This program incorporates a modified version of Twidere.
 * Copyright (C) 2012-2015 Mariotaku Lee <mariotaku.lee@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.vanita5.twittnuker.view;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.Drawable;
import android.support.v7.widget.AppCompatTextView;
import android.util.AttributeSet;

import de.vanita5.twittnuker.R;
import de.vanita5.twittnuker.util.support.TextViewSupport;

public class ActionIconThemedTextView extends AppCompatTextView {

    private final int mIconWidth, mIconHeight;
    private int mColor, mDisabledColor, mActivatedColor;

	public ActionIconThemedTextView(Context context) {
		this(context, null);
	}

	public ActionIconThemedTextView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public ActionIconThemedTextView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.IconActionButton);
        mColor = a.getColor(R.styleable.IconActionButton_iabColor, 0);
        mDisabledColor = a.getColor(R.styleable.IconActionButton_iabDisabledColor, 0);
        mActivatedColor = a.getColor(R.styleable.IconActionButton_iabActivatedColor, 0);
        mIconWidth = a.getDimensionPixelSize(R.styleable.IconActionButton_iabIconWidth, 0);
        mIconHeight = a.getDimensionPixelSize(R.styleable.IconActionButton_iabIconHeight, 0);
		a.recycle();
        updateCompoundDrawables();
	}

	public int getActivatedColor() {
        if (mActivatedColor != 0) return mActivatedColor;
        final ColorStateList colors = getLinkTextColors();
        if (colors != null) return colors.getDefaultColor();
        return getCurrentTextColor();
	}

    @Override
    public void setCompoundDrawablesWithIntrinsicBounds(Drawable left, Drawable top, Drawable right, Drawable bottom) {
        super.setCompoundDrawablesWithIntrinsicBounds(left, top, right, bottom);
        updateCompoundDrawables();
    }

    @Override
    public void setCompoundDrawablesWithIntrinsicBounds(int left, int top, int right, int bottom) {
        super.setCompoundDrawablesWithIntrinsicBounds(left, top, right, bottom);
        updateCompoundDrawables();
    }

    @Override
    public void setCompoundDrawablesRelativeWithIntrinsicBounds(Drawable start, Drawable top, Drawable end, Drawable bottom) {
        super.setCompoundDrawablesRelativeWithIntrinsicBounds(start, top, end, bottom);
        updateCompoundDrawables();
    }

    @Override
    public void setCompoundDrawablesRelativeWithIntrinsicBounds(int start, int top, int end, int bottom) {
        super.setCompoundDrawablesRelativeWithIntrinsicBounds(start, top, end, bottom);
        updateCompoundDrawables();
    }

	public int getColor() {
        if (mColor != 0) return mColor;
        final ColorStateList colors = getTextColors();
        if (colors != null) return colors.getDefaultColor();
        return getCurrentTextColor();
	}

    public int getDisabledColor() {
        if (mDisabledColor != 0) return mDisabledColor;
        final ColorStateList colors = getTextColors();
        if (colors != null) return colors.getColorForState(new int[0], colors.getDefaultColor());
        return getCurrentTextColor();
    }

	@Override
	public void setActivated(boolean activated) {
		super.setActivated(activated);
	}

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        updateCompoundDrawables();
    }

    private void updateCompoundDrawables() {
        updateCompoundDrawables(getCompoundDrawables());
        updateCompoundDrawables(TextViewSupport.getCompoundDrawablesRelative(this));
    }

    private void updateCompoundDrawables(Drawable[] drawables) {
        if (drawables == null) return;
        for (Drawable d : drawables) {
            if (d == null) continue;
			d.mutate();
			final int color;
			if (isActivated()) {
				color = getActivatedColor();
			} else if (isEnabled()) {
				color = getColor();
			} else {
				color = getDisabledColor();
			}
            if (mIconWidth > 0 && mIconHeight > 0) {
                d.setBounds(0, 0, mIconWidth, mIconHeight);
            }
			d.setColorFilter(color, Mode.SRC_ATOP);
		}
	}

}