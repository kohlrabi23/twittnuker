/*
 *				Twidere - Twitter client for Android
 * 
 * Copyright (C) 2012 Mariotaku Lee <mariotaku.lee@gmail.com>
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

package de.vanita5.twittnuker.preference;

import android.content.Context;
import android.util.AttributeSet;

import de.vanita5.twittnuker.Constants;
import de.vanita5.twittnuker.R;

public class NotificationContentPreference extends MultiSelectListPreference implements Constants {

	public NotificationContentPreference(final Context context) {
		this(context, null);
	}

	public NotificationContentPreference(final Context context, final AttributeSet attrs) {
		this(context, attrs, android.R.attr.preferenceStyle);
	}

	public NotificationContentPreference(final Context context, final AttributeSet attrs, final int defStyle) {
		super(context, attrs, defStyle);
	}

	@Override
	protected boolean[] getDefaults() {
		return new boolean[] { PREFERENCE_DEFAULT_HOME_TIMELINE_NOTIFICATION, PREFERENCE_DEFAULT_MENTIONS_NOTIFICATION,
				PREFERENCE_DEFAULT_DIRECT_MESSAGES_NOTIFICATION };
	}

	@Override
	protected String[] getKeys() {
		return new String[] { PREFERENCE_KEY_HOME_TIMELINE_NOTIFICATION, PREFERENCE_KEY_MENTIONS_NOTIFICATION,
				PREFERENCE_KEY_DIRECT_MESSAGES_NOTIFICATION };
	}

	@Override
	protected String[] getNames() {
		return getContext().getResources().getStringArray(R.array.entries_notification_content);
	}

}