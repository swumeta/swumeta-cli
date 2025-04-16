/*
 * Copyright (c) 2025 swumeta.net authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.swumeta.cli.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import net.swumeta.cli.AppException;
import org.apache.tomcat.util.collections.CaseInsensitiveKeyMap;

import java.util.Locale;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Location(
        @JsonProperty(required = true) String country,
        String city
) {
    private static final CaseInsensitiveKeyMap<String> COUNTRY_FLAG_CACHE = new CaseInsensitiveKeyMap<>();

    @Override
    public String toString() {
        if (city == null) {
            return country;
        }
        return "%s (%s)".formatted(city, country);
    }

    public String countryFlag() {
        if ("USA".equals(country)) {
            return "us";
        }
        String flag = COUNTRY_FLAG_CACHE.get(country);
        if (flag != null) {
            return flag;
        }

        for (final var iso : Locale.getISOCountries()) {
            final var locale = new Locale("", iso);
            if (locale.getDisplayCountry(Locale.ENGLISH).equalsIgnoreCase(country)) {
                flag = iso.toLowerCase(Locale.ENGLISH);
                break;
            }
        }
        if (flag == null) {
            throw new AppException("Unable to find country code: " + country);
        }
        COUNTRY_FLAG_CACHE.put(country, flag);
        return flag;
    }
}
