/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.helidon.tests.integration.dbclient.common.model;

import java.util.List;

/**
 * {@code Pokemon} data set.
 */
@SuppressWarnings("SpellCheckingInspection")
public final class Pokemons {

    private Pokemons() {
        // cannot be instantiated
    }

    /**
     * {@code Pikachu}.
     */
    public static final Pokemon PIKACHU = new Pokemon(1, "Pikachu", Types.ELECTRIC);

    /**
     * {@code Raichu}.
     */
    public static final Pokemon RAICHU = new Pokemon(2, "Raichu", Types.ELECTRIC);

    /**
     * {@code Machop}.
     */
    public static final Pokemon MACHOP = new Pokemon(3, "Machop", Types.FIGHTING);

    /**
     * {@code Snorlax}.
     */
    public static final Pokemon SNORLAX = new Pokemon(4, "Snorlax", Types.NORMAL);

    /**
     * {@code Charizard}.
     */
    public static final Pokemon CHARIZARD = new Pokemon(5, "Charizard", Types.FIRE, Types.FLYING);

    /**
     * {@code Meowth}.
     */
    public static final Pokemon MEOWTH = new Pokemon(6, "Meowth", Types.NORMAL);

    /**
     * {@code Gyarados}.
     */
    public static final Pokemon GYARADOS = new Pokemon(7, "Gyarados", Types.FLYING, Types.WATER);

    /**
     * {@code Spearow}.
     */
    public static final Pokemon SPEAROW = new Pokemon(8, "Spearow", Types.NORMAL, Types.FLYING);

    /**
     * {@code Fearow}.
     */
    public static final Pokemon FEAROW = new Pokemon(9, "Fearow", Types.NORMAL, Types.FLYING);

    /**
     * {@code Ekans}.
     */
    public static final Pokemon EKANS = new Pokemon(10, "Ekans", Types.POISON);

    /**
     * {@code Arbok}.
     */
    public static final Pokemon ARBOK = new Pokemon(11, "Arbok", Types.POISON);

    /**
     * {@code Sandshrew}.
     */
    public static final Pokemon SANDSHREW = new Pokemon(12, "Sandshrew", Types.GROUND);

    /**
     * {@code Sandslash}.
     */
    public static final Pokemon SANDSLASH = new Pokemon(13, "Sandslash", Types.GROUND);

    /**
     * {@code Diglett}.
     */
    public static final Pokemon DIGLETT = new Pokemon(14, "Diglett", Types.GROUND);

    /**
     * {@code Rayquaza}.
     */
    public static final Pokemon RAYQUAZA = new Pokemon(15, "Rayquaza", Types.FLYING, Types.DRAGON);

    /**
     * {@code Lugia}.
     */
    public static final Pokemon LUGIA = new Pokemon(16, "Lugia", Types.FLYING, Types.PSYCHIC);

    /**
     * {@code Ho-Oh}.
     */
    public static final Pokemon HOOH = new Pokemon(17, "Ho-Oh", Types.FLYING, Types.FIRE);

    /**
     * {@code Raikou}.
     */
    public static final Pokemon RAIKOU = new Pokemon(18, "Raikou", Types.ELECTRIC);

    /**
     * {@code Giratina}.
     */
    public static final Pokemon GIRATINA = new Pokemon(19, "Giratina", Types.GHOST, Types.DRAGON);

    /**
     * {@code Regirock}.
     */
    public static final Pokemon REGIROCK = new Pokemon(20, "Regirock", Types.ROCK);

    /**
     * {@code Kyogre}.
     */
    public static final Pokemon KYOGRE = new Pokemon(21, "Kyogre", Types.WATER);

    /**
     * {@code Piplup}.
     */
    public static final Pokemon PIPLUP = new Pokemon(29, "Piplup", Types.WATER);

    /**
     * {@code Prinplup}.
     */
    public static final Pokemon PRINPLUP = new Pokemon(30, "Prinplup", Types.WATER);

    /**
     * {@code Empoleon}.
     */
    public static final Pokemon EMPOLEON = new Pokemon(31, "Empoleon", Types.STEEL, Types.WATER);

    /**
     * {@code Staryu}.
     */
    public static final Pokemon STARYU = new Pokemon(32, "Staryu", Types.WATER);

    /**
     * {@code Starmie}.
     */
    public static final Pokemon STARMIE = new Pokemon(33, "Starmie", Types.WATER, Types.PSYCHIC);

    /**
     * {@code Horsea}.
     */
    public static final Pokemon HORSEA = new Pokemon(34, "Horsea", Types.WATER);

    /**
     * {@code Seadra}.
     */
    public static final Pokemon SEADRA = new Pokemon(35, "Seadra", Types.WATER);

    /**
     * {@code Mudkip}.
     */
    public static final Pokemon MUDKIP = new Pokemon(36, "Mudkip", Types.WATER);

    /**
     * {@code Marshtomp}.
     */
    public static final Pokemon MARSHTOMP = new Pokemon(37, "Marshtomp", Types.GROUND, Types.WATER);

    /**
     * {@code Swampert}.
     */
    public static final Pokemon SWAMPERT = new Pokemon(38, "Swampert", Types.GROUND, Types.WATER);

    /**
     * {@code Muk}.
     */
    public static final Pokemon MUK = new Pokemon(39, "Muk", Types.POISON);

    /**
     * {@code Grimer}.
     */
    public static final Pokemon GRIMER = new Pokemon(40, "Grimer", Types.POISON);

    /**
     * {@code Cubchoo}.
     */
    public static final Pokemon CUBCHOO = new Pokemon(41, "Cubchoo", Types.ICE);

    /**
     * {@code Beartic}.
     */
    public static final Pokemon BEARTIC = new Pokemon(42, "Beartic", Types.ICE);

    /**
     * {@code Shinx}.
     */
    public static final Pokemon SHINX = new Pokemon(50, "Shinx", Types.ELECTRIC);

    /**
     * {@code Luxio}.
     */
    public static final Pokemon LUXIO = new Pokemon(51, "Luxio", Types.ELECTRIC);

    /**
     * {@code Luxray}.
     */
    public static final Pokemon LUXRAY = new Pokemon(52, "Luxray", Types.ELECTRIC);

    /**
     * {@code Kricketot}.
     */
    public static final Pokemon KRICKETOT = new Pokemon(53, "Kricketot", Types.GHOST);

    /**
     * {@code Kricketune}.
     */
    public static final Pokemon KRICKETUNE = new Pokemon(54, "Kricketune", Types.GHOST);

    /**
     * {@code Phione}.
     */
    public static final Pokemon PHIONE = new Pokemon(55, "Phione", Types.WATER);

    /**
     * {@code Chatot}.
     */
    public static final Pokemon CHATOT = new Pokemon(56, "Chatot", Types.NORMAL, Types.FLYING);

    /**
     * {@code Teddiursa}.
     */
    public static final Pokemon TEDDIURSA = new Pokemon(57, "Teddiursa", Types.NORMAL);

    /**
     * {@code Ursaring}.
     */
    public static final Pokemon URSARING = new Pokemon(58, "Ursaring", Types.NORMAL);

    /**
     * {@code Slugma}.
     */
    public static final Pokemon SLUGMA = new Pokemon(59, "Slugma", Types.FIRE);

    /**
     * {@code Magcargo}.
     */
    public static final Pokemon MAGCARGO = new Pokemon(60, "Magcargo", Types.ROCK, Types.FIRE);

    /**
     * {@code Lotad}.
     */
    public static final Pokemon LOTAD = new Pokemon(61, "Lotad", Types.WATER, Types.GRASS);

    /**
     * {@code Lombre}.
     */
    public static final Pokemon LOMBRE = new Pokemon(62, "Lombre", Types.WATER, Types.GRASS);

    /**
     * {@code Ludicolo}.
     */
    public static final Pokemon LUDICOLO = new Pokemon(63, "Ludicolo", Types.WATER, Types.GRASS);

    /**
     * {@code Natu}.
     */
    public static final Pokemon NATU = new Pokemon(64, "Natu", Types.FLYING, Types.PSYCHIC);

    /**
     * {@code Xatu}.
     */
    public static final Pokemon XATU = new Pokemon(65, "Xatu", Types.FLYING, Types.PSYCHIC);

    /**
     * {@code Snubbull}.
     */
    public static final Pokemon SNUBBULL = new Pokemon(66, "Snubbull", Types.FAIRY);

    /**
     * {@code Granbull}.
     */
    public static final Pokemon GRANBULL = new Pokemon(67, "Granbull", Types.FAIRY);

    /**
     * {@code Entei}.
     */
    public static final Pokemon ENTEI = new Pokemon(68, "Entei", Types.FIRE);

    /**
     * {@code Suicune}.
     */
    public static final Pokemon SUICUNE = new Pokemon(70, "Suicune", Types.WATER);

    /**
     * {@code Omanyte}.
     */
    public static final Pokemon OMANYTE = new Pokemon(71, "Omanyte", Types.ROCK, Types.WATER);

    /**
     * {@code Omastar}.
     */
    public static final Pokemon OMASTAR = new Pokemon(72, "Omastar", Types.ROCK, Types.WATER);

    /**
     * {@code Kabuto}.
     */
    public static final Pokemon KABUTO = new Pokemon(73, "Kabuto", Types.ROCK, Types.WATER);

    /**
     * {@code Kabutops}.
     */
    public static final Pokemon KABUTOPS = new Pokemon(74, "Kabutops", Types.ROCK, Types.WATER);

    /**
     * {@code Chikorita}.
     */
    public static final Pokemon CHIKORITA = new Pokemon(75, "Chikorita", Types.GRASS);

    /**
     * {@code Bayleef}.
     */
    public static final Pokemon BAYLEEF = new Pokemon(76, "Bayleef", Types.GRASS);

    /**
     * {@code Meganium}.
     */
    public static final Pokemon MEGANIUM = new Pokemon(77, "Meganium", Types.GRASS);

    /**
     * {@code Trapinch}.
     */
    public static final Pokemon TRAPINCH = new Pokemon(78, "Trapinch", Types.GROUND);

    /**
     * {@code Vibrava}.
     */
    public static final Pokemon VIBRAVA = new Pokemon(79, "Vibrava", Types.GROUND, Types.DRAGON);

    /**
     * {@code Spoink}.
     */
    public static final Pokemon SPOINK = new Pokemon(80, "Spoink", Types.PSYCHIC);

    /**
     * {@code Grumpig}.
     */
    public static final Pokemon GRUMPIG = new Pokemon(81, "Grumpig", Types.PSYCHIC);

    /**
     * {@code Beldum}.
     */
    public static final Pokemon BELDUM = new Pokemon(82, "Beldum", Types.STEEL, Types.PSYCHIC);

    /**
     * {@code Metang}.
     */
    public static final Pokemon METANG = new Pokemon(83, "Metang", Types.STEEL, Types.PSYCHIC);

    /**
     * {@code Metagross}.
     */
    public static final Pokemon METAGROSS = new Pokemon(84, "Metagross", Types.STEEL, Types.PSYCHIC);

    /**
     * {@code Moltres}.
     */
    public static final Pokemon MOLTRES = new Pokemon(99, "Moltres", Types.FLYING, Types.FIRE);

    /**
     * {@code Masquerain}.
     */
    public static final Pokemon MASQUERAIN = new Pokemon(100, "Masquerain", Types.FLYING, Types.GHOST);

    /**
     * {@code Makuhita}.
     */
    public static final Pokemon MAKUHITA = new Pokemon(101, "Makuhita", Types.FIGHTING);

    /**
     * {@code Hariyama}.
     */
    public static final Pokemon HARIYAMA = new Pokemon(102, "Hariyama", Types.FIGHTING);

    /**
     * All {@code pokemons}.
     */
    public static final List<Pokemon> ALL = List.of(
            PIKACHU, RAICHU, MACHOP, SNORLAX, CHARIZARD, MEOWTH, GYARADOS, SPEAROW, FEAROW, EKANS, ARBOK, SANDSHREW, SANDSLASH,
            DIGLETT, RAYQUAZA, LUGIA, HOOH, RAIKOU, GIRATINA, REGIROCK, KYOGRE, PIPLUP, PRINPLUP, EMPOLEON, STARYU, STARMIE,
            HORSEA, SEADRA, MUDKIP, MARSHTOMP, SWAMPERT, MUK, GRIMER, CUBCHOO, BEARTIC, SHINX, LUXIO, LUXRAY, KRICKETOT,
            KRICKETUNE, PHIONE, CHATOT, TEDDIURSA, URSARING, SLUGMA, MAGCARGO, LOTAD, LOMBRE, LUDICOLO, NATU, XATU, SNUBBULL,
            GRANBULL, ENTEI, SUICUNE, OMANYTE, OMASTAR, KABUTO, KABUTOPS, CHIKORITA, BAYLEEF, MEGANIUM, TRAPINCH, VIBRAVA,
            SPOINK, GRUMPIG, BELDUM, METANG, METAGROSS, MOLTRES, MASQUERAIN, MAKUHITA, HARIYAMA);
}
