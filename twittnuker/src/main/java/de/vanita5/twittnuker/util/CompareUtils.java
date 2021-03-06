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

package de.vanita5.twittnuker.util;

import android.os.Bundle;

import org.apache.commons.lang3.ArrayUtils;

public class CompareUtils {

	public static boolean bundleEquals(final Bundle bundle1, final Bundle bundle2, final String... ignoredKeys) {
		if (bundle1 == null || bundle2 == null) return bundle1 == bundle2;
        for (String key : bundle1.keySet()) {
            if (!ArrayUtils.contains(ignoredKeys, key) && !objectEquals(bundle1.get(key), bundle2.get(key)))
				return false;
		}
		return true;
	}

	public static boolean classEquals(final Class<?> cls1, final Class<?> cls2) {
		if (cls1 == null || cls2 == null) return cls1 == cls2;
		return cls1.getName().equals(cls2.getName());
	}

	public static boolean objectEquals(final Object object1, final Object object2) {
		if (object1 == null || object2 == null) return object1 == object2;
		if (object1 instanceof Bundle && object2 instanceof Bundle)
			return bundleEquals((Bundle) object1, (Bundle) object2);
		else if (object1 instanceof Class && object2 instanceof Class)
			return classEquals((Class<?>) object1, (Class<?>) object2);
		return object1.equals(object2);
	}

    public static boolean textEquals(CharSequence text1, CharSequence text2) {
        if (text1 == null || text2 == null) return text1 == text2;
        if (text1 == text2) return true;
        if (text1.length() != text2.length()) return false;
        for (int i = 0, j = text1.length(); i < j; i++) {
            if (text1.charAt(i) != text2.charAt(i)) return false;
		}
        return true;
    }
}