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

package de.vanita5.twittnuker.activity;

import android.app.Activity;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.os.Bundle;
import android.support.v4.app.NavUtils;

import de.vanita5.twittnuker.activity.iface.IThemedActivity;
import de.vanita5.twittnuker.util.CompareUtils;
import de.vanita5.twittnuker.util.StrictModeUtils;
import de.vanita5.twittnuker.util.ThemeUtils;
import de.vanita5.twittnuker.util.Utils;

import static de.vanita5.twittnuker.util.Utils.restartActivity;

public abstract class BaseThemedActivity extends Activity implements IThemedActivity {

    private int mCurrentThemeResource;
    private int mCurrentThemeColor;
    private int mCurrentThemeBackgroundAlpha;
	private int mCurrentActionBarColor;
	private String mCurrentThemeFontFamily;
    private String mCurrentThemeBackgroundOption;

	@Override
    public int getCurrentThemeBackgroundAlpha() {
        return mCurrentThemeBackgroundAlpha;
	}

	@Override
    public int getCurrentThemeColor() {
        return mCurrentThemeColor;
    }

    @Override
	public final int getCurrentThemeResourceId() {
		return mCurrentThemeResource;
	}

    @Override
    public int getThemeBackgroundAlpha() {
        return ThemeUtils.getUserThemeBackgroundAlpha(this);
    }

    @Override
    public abstract int getThemeColor();

	public abstract int getActionBarColor();

	@Override
	public String getThemeFontFamily() {
		return ThemeUtils.getThemeFontFamily(this);
	}

    @Override
    public abstract int getThemeResourceId();

	@Override
	public final void restart() {
		restartActivity(this);
	}

	protected final boolean isThemeChanged() {
		return getThemeResourceId() != mCurrentThemeResource || getThemeColor() != mCurrentThemeColor
                || !CompareUtils.objectEquals(getThemeFontFamily(), mCurrentThemeFontFamily)
                || getThemeBackgroundAlpha() != mCurrentThemeBackgroundAlpha
				|| getActionBarColor() != mCurrentActionBarColor;
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		if (Utils.isDebugBuild()) {
			StrictModeUtils.detectAllVmPolicy();
			StrictModeUtils.detectAllThreadPolicy();
		}

		setTheme();
		super.onCreate(savedInstanceState);
		setActionBarBackground();
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (isThemeChanged()) {
			restart();
		}
	}

    private void setActionBarBackground() {
        ThemeUtils.applyActionBarBackground(getActionBar(), this, mCurrentThemeResource);
    }

    private void setTheme() {
		mCurrentThemeResource = getThemeResourceId();
		mCurrentThemeColor = getThemeColor();
		mCurrentActionBarColor = getActionBarColor();
		mCurrentThemeFontFamily = getThemeFontFamily();
        mCurrentThemeBackgroundAlpha = getThemeBackgroundAlpha();
        mCurrentThemeBackgroundOption = getThemeBackgroundOption();
		setTheme(mCurrentThemeResource);
        ThemeUtils.applyWindowBackground(this, getWindow(), mCurrentThemeResource, mCurrentThemeBackgroundOption, mCurrentThemeBackgroundAlpha);
	}
}