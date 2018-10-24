/*
 * ToroDB
 * Copyright © 2014 8Kdata Technology (www.8kdata.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.torodb.core.d2r;

import com.torodb.core.TableRef;
import com.torodb.core.backend.IdentifierConstraints;
import com.torodb.core.exceptions.SystemException;

import java.text.Normalizer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Pattern;

import javax.inject.Inject;

public class UniqueIdentifierGenerator {

  private static final int MAX_GENERATION_TIME = 10;

  private final IdentifierConstraints identifierConstraints;
  private final char separator;
  private final String separatorString;
  private final char arrayDimensionSeparator;

  @Inject
  public UniqueIdentifierGenerator(IdentifierConstraints identifierConstraints) {
    this.identifierConstraints = identifierConstraints;
    this.separator = identifierConstraints.getSeparator();
    this.separatorString = String.valueOf(separator);
    this.arrayDimensionSeparator = identifierConstraints.getArrayDimensionSeparator();
  }
  
  public IdentifierConstraints getIdentifierConstraints() {
    return identifierConstraints;
  }

  public NameChain createNameChain() {
    return new NameChain(separatorString);
  }

  public String generateUniqueIdentifier(NameChain nameChain,
      IdentifierChecker uniqueIdentifierChecker) {
    return generateIdentifier(nameChain, uniqueIdentifierChecker, null);
  }

  public String generateIdentifier(NameChain nameChain, IdentifierChecker identifierChecker,
      String extraImmutableName) {
    return generateIdentifier(nameChain, identifierChecker, extraImmutableName, 
        ChainConverterFactory.straight, ChainConverterFactory.counter);
  }

  public String generateIdentifier(NameChain nameChain, IdentifierChecker identifierChecker,
      String extraImmutableName, ChainConverterFactory primaryConverterFactory, 
      ChainConverterFactory secondaryChainConverterFactory) {
    final Instant beginInstant = Instant.now();
    final int maxSize = identifierConstraints.identifierMaxSize();
    String lastCollision = null;
    Counter counter = new Counter();
    String identifier =
        buildIdentifier(nameChain, primaryConverterFactory.getConverters(), maxSize, counter,
            identifierChecker, extraImmutableName);

    if (identifier.length() <= maxSize && identifierChecker.isUnique(identifier)) {
      return identifier;
    }

    if (identifier.length() <= maxSize) {
      lastCollision = identifier;
    }

    NameConverter[] counterConverters = secondaryChainConverterFactory.getConverters();
    while (ChronoUnit.SECONDS.between(beginInstant, Instant.now()) < MAX_GENERATION_TIME) {
      identifier =
          buildIdentifier(nameChain, counterConverters, maxSize, counter, identifierChecker,
              extraImmutableName);

      if (identifier.length() > maxSize) {
        throw new SystemException("Counter generator did not fit in maxSize!");
      }

      if (identifierChecker.isUnique(identifier)) {
        return identifier;
      }

      lastCollision = identifier;

      counter.increment();
    }

    if (lastCollision != null) {
      throw new SystemException(
          "Identifier collision(s) does not allow to generate a valid identifier. Last "
          + "collisioned identifier: " + lastCollision + ". Name chain: " + nameChain);
    }

    throw new SystemException("Can not generate a valid identifier. Name chain: " + nameChain);
  }

  public void append(NameChain nameChain, TableRef tableRef) {
    if (tableRef.isRoot()) {
      return;
    }

    TableRef parentTableRef = tableRef.getParent().get();
    String name = tableRef.getName();
    if (tableRef.isInArray()) {
      while (parentTableRef.isInArray()) {
        parentTableRef = parentTableRef.getParent().get();
      }
      name = parentTableRef.getName() + arrayDimensionSeparator + tableRef.getArrayDimension();

      parentTableRef = parentTableRef.getParent().get();
    }

    append(nameChain, parentTableRef);

    nameChain.add(name);
  }

  private String buildIdentifier(NameChain nameChain, NameConverter[] converters, int maxSize,
      Counter counter, IdentifierChecker identifierChecker, String extraImmutableName) {
    final int nameMaxSize = extraImmutableName != null ? maxSize - extraImmutableName.length() - 1 :
        maxSize;
    StringBuilder middleIdentifierBuilder = new StringBuilder();

    final int size = nameChain.size();
    int index = 1;
    for (; index < size - 2; index++) {
      if (converters[1].convertWhole()) {
        middleIdentifierBuilder.append(nameChain.get(index));
      } else {
        middleIdentifierBuilder.append(converters[1].convert(nameChain.get(index), separator,
            nameMaxSize, counter));
      }
      middleIdentifierBuilder.append(separator);
    }
    if (index < size - 1) {
      if (converters[1].convertWhole()) {
        middleIdentifierBuilder.append(nameChain.get(index));
      } else {
        middleIdentifierBuilder.append(converters[1].convert(nameChain.get(index), separator,
            nameMaxSize, counter));
      }
      index++;
    }

    StringBuilder identifierBuilder = new StringBuilder();

    if (converters[0].convertWhole()) {
      if (converters[0] == converters[1] && converters[0] == converters[2]) {
        StringBuilder intermediateIdentifierBuilder = new StringBuilder();
        intermediateIdentifierBuilder.append(nameChain.get(0));
        if (middleIdentifierBuilder.length() > 0) {
          intermediateIdentifierBuilder.append(separator);
          intermediateIdentifierBuilder.append(middleIdentifierBuilder);
        }
        if (index < size) {
          intermediateIdentifierBuilder.append(separator);
          intermediateIdentifierBuilder.append(nameChain.get(size - 1));
        }
        identifierBuilder.append(converters[0].convert(intermediateIdentifierBuilder.toString(),
            separator, nameMaxSize, counter));
      } else if (converters[0] == converters[1]) {
        StringBuilder intermediateIdentifierBuilder = new StringBuilder();
        intermediateIdentifierBuilder.append(nameChain.get(0));
        if (middleIdentifierBuilder.length() > 0) {
          intermediateIdentifierBuilder.append(separator);
          intermediateIdentifierBuilder.append(middleIdentifierBuilder);
        }
        identifierBuilder.append(converters[0].convert(intermediateIdentifierBuilder.toString(),
            separator, nameMaxSize, counter));
        if (index < size) {
          identifierBuilder.append(separator);
          identifierBuilder.append(converters[2].convert(nameChain.get(size - 1), separator,
              nameMaxSize, counter));
        }
      } else {
        identifierBuilder.append(converters[0].convert(nameChain.get(0), separator, nameMaxSize,
            counter));
        if (middleIdentifierBuilder.length() > 0) {
          identifierBuilder.append(separator);
          identifierBuilder.append(middleIdentifierBuilder);
        }
        if (index < size) {
          identifierBuilder.append(separator);
          identifierBuilder.append(converters[2].convert(nameChain.get(size - 1), separator,
              nameMaxSize, counter));
        }
      }
    } else if (converters[1].convertWhole()) {
      if (converters[1] == converters[2]) {
        identifierBuilder.append(converters[0].convert(nameChain.get(0), separator, nameMaxSize,
            counter));
        StringBuilder intermediateIdentifierBuilder = new StringBuilder();
        if (middleIdentifierBuilder.length() > 0) {
          intermediateIdentifierBuilder.append(middleIdentifierBuilder);
        }
        if (index < size) {
          intermediateIdentifierBuilder.append(separator);
          intermediateIdentifierBuilder.append(nameChain.get(size - 1));
        }
        if (intermediateIdentifierBuilder.length() > 0) {
          identifierBuilder.append(separator);
          identifierBuilder.append(converters[1].convert(intermediateIdentifierBuilder.toString(),
              separator, nameMaxSize, counter));
        }
      } else {
        identifierBuilder.append(converters[0].convert(nameChain.get(0), separator, nameMaxSize,
            counter));
        if (middleIdentifierBuilder.length() > 0) {
          identifierBuilder.append(separator);
          identifierBuilder.append(converters[1].convert(middleIdentifierBuilder.toString(),
              separator, nameMaxSize, counter));
        }
        if (index < size) {
          identifierBuilder.append(separator);
          identifierBuilder.append(nameChain.get(size - 1));
        }
      }
    } else {
      identifierBuilder.append(converters[0].convert(nameChain.get(0), separator, nameMaxSize,
          counter));
      if (middleIdentifierBuilder.length() > 0) {
        identifierBuilder.append(separator);
        identifierBuilder.append(middleIdentifierBuilder);
      }
      if (index < size) {
        identifierBuilder.append(separator);
        identifierBuilder.append(nameChain.get(size - 1));
      }
    }

    if (extraImmutableName != null) {
      identifierBuilder.append(separator).append(extraImmutableName);
    }

    String identifier = identifierBuilder.toString();

    if (!identifierChecker.isAllowed(identifierConstraints, identifier)) {
      identifier = separator + identifier;
    }

    return identifier;
  }

  public static class NameChain {

    private static final Pattern NO_ALLOWED_CHAR_PATTERN = Pattern.compile("[^0-9a-z_$]");
    private final String separatorString;
    private final ArrayList<String> names;

    private NameChain(String separatorString) {
      this.separatorString = separatorString;
      names = new ArrayList<>();
    }

    public void add(String e) {
      e = Normalizer.normalize(e, Normalizer.Form.NFD);
      e = NO_ALLOWED_CHAR_PATTERN.matcher(e
          .toLowerCase(Locale.US))
          .replaceAll(separatorString);

      names.add(e);
    }

    public String get(int index) {
      return names.get(index);
    }

    public int size() {
      return names.size();
    }
  }

  private static final NameConverters NameConverters = new NameConverters();

  public static enum ChainConverterFactory {
    straight(NameConverters.straight),
    straight_cutvowels(NameConverters.straight, NameConverters.cutvowels, NameConverters.straight),
    @SuppressWarnings("checkstyle:LineLength")
    straight_singlechar(NameConverters.straight, NameConverters.singlechar, NameConverters.straight),
    straight_hash(NameConverters.straight, NameConverters.hash, NameConverters.straight),
    first_straight_hash(NameConverters.straight, NameConverters.hash),
    cutvowels(NameConverters.cutvowels),
    cutvowels_singlechar(NameConverters.cutvowels, NameConverters.singlechar,
        NameConverters.cutvowels),
    cutvowels_hash(NameConverters.cutvowels, NameConverters.hash, NameConverters.cutvowels),
    first_cutvowels_hash(NameConverters.cutvowels, NameConverters.hash),
    hash(NameConverters.hash),
    hash_and_random(NameConverters.hashAndRandom),
    counter(NameConverters.counter),
    random_cut(NameConverters.randomCut);

    private final NameConverter[] converters;

    private ChainConverterFactory(NameConverter... nameConverterFactories) {
      this.converters = createConverters(nameConverterFactories);
    }

    private NameConverter[] getConverters() {
      return converters;
    }

    private NameConverter[] createConverters(NameConverter[] converterFactories) {
      NameConverter[] converters = new NameConverter[3];

      converters[0] = converterFactories[0];
      converters[1] = converters[0];
      converters[2] = converters[0];

      if (converterFactories.length > 1) {
        if (converterFactories[0] == converterFactories[1]) {
          converters[1] = converters[0];
        } else {
          converters[1] = converterFactories[1];
        }

        converters[2] = converters[0];
      }

      if (converterFactories.length > 2) {
        if (converterFactories[0] == converterFactories[2]) {
          converters[2] = converters[0];
        } else if (converterFactories[1] == converterFactories[2]) {
          converters[2] = converters[1];
        } else {
          converters[2] = converterFactories[2];
        }
      }

      return converters;
    }
  }

  private static class NameConverters {

    public NameConverter straight = new NameConverter() {
      @Override
      public String convert(String name, char separator, int maxSize, Counter counter) {
        return name;
      }
    };

    public NameConverter cutvowels = new NameConverter() {
      private final Pattern pattern = Pattern.compile("([a-z])[aeiou]+");

      @Override
      public String convert(String name, char separator, int maxSize, Counter counter) {
        return pattern.matcher(name).replaceAll("$1");
      }
    };

    public NameConverter singlechar = new NameConverter() {
      @Override
      public String convert(String name, char separator, int maxSize, Counter counter) {
        if (name.isEmpty()) {
          return name;
        }

        return "" + name.charAt(0);
      }
    };

    public NameConverter hash = new NameConverter() {
      @Override
      public String convert(String name, char separator, int maxSize, Counter counter) {
        String value = separator + 'x' + Integer.toHexString(name.hashCode());

        if (name.length() + value.length() < maxSize) {
          return name + value;
        }

        int availableSize = Math.min(name.length(), maxSize) - value.length();
        return name.substring(0, availableSize / 2 + availableSize % 2)
            + name.substring(name.length() - availableSize / 2, name.length()) + value;
      }

      @Override
      public boolean convertWhole() {
        return true;
      }
    };

    public NameConverter hashAndRandom = new NameConverter() {
      private final Random random = new Random();

      @Override
      public String convert(String name, char separator, int maxSize, Counter counter) {
        String value = separator + 'x' + Integer.toHexString(name.hashCode()) + separator + 'r'
            + Integer.toHexString(random.nextInt());

        if (name.length() + value.length() < maxSize) {
          return name + value;
        }

        int availableSize = Math.min(name.length(), maxSize) - value.length();
        return name.substring(0, availableSize / 2 + availableSize % 2)
            + name.substring(name.length() - availableSize / 2, name.length()) + value;
      }

      @Override
      public boolean convertWhole() {
        return true;
      }
    };

    public NameConverter counter = new NameConverter() {
      @Override
      public String convert(String name, char separator, int maxSize, Counter counter) {
        String value = separator + String.valueOf(counter.get());

        if (name.length() + value.length() < maxSize) {
          return name + value;
        }

        int availableSize = Math.min(name.length(), maxSize) - value.length();
        return name.substring(0, availableSize / 2 + availableSize % 2)
            + name.substring(name.length() - availableSize / 2, name.length()) + value;
      }

      @Override
      public boolean convertWhole() {
        return true;
      }
    };

    public NameConverter randomCut = new NameConverter() {
      private final Random random = new Random();

      @Override
      public String convert(String name, char separator, int maxSize, Counter counter) {
        if (name.length() < maxSize) {
          return name;
        }
        
        String value = separator + Integer.toHexString(random.nextInt());
        
        int availableSize = Math.min(name.length(), maxSize) - value.length();
        return name.substring(0, availableSize / 2 + availableSize % 2)
            + name.substring(name.length() - availableSize / 2, name.length()) + value;
      }

      @Override
      public boolean convertWhole() {
        return true;
      }
    };

  }

  private abstract static class NameConverter {

    public abstract String convert(String name, char separator, int maxSize, Counter counter);

    public boolean convertWhole() {
      return false;
    }
  }

  private static class Counter {

    private int counter = 1;

    public int get() {
      return counter;
    }

    public void increment() {
      counter++;
    }
  }

  public static interface IdentifierChecker {

    boolean isUnique(String identifier);

    boolean isAllowed(IdentifierConstraints identifierInterface, String identifier);
  }

}
