package com.codingame.gameengine.core;

import javax.inject.Provider;
import javax.inject.Singleton;

import org.codejargon.feather.Provides;

import com.google.gson.Gson;

class GameEngineModule {

    @SuppressWarnings("unchecked")
    private Class<? extends AbstractPlayer> getPlayerClass() throws ClassNotFoundException {
        return (Class<? extends AbstractPlayer>) Class.forName("com.codingame.game.Player");
    }

    @SuppressWarnings("unchecked")
    private Class<? extends AbstractReferee> getRefereeClass() throws ClassNotFoundException {
        return (Class<? extends AbstractReferee>) Class.forName("com.codingame.game.Referee");
    }

    @Provides
    @Singleton
    AbstractReferee provideAbstractReferee() throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        return getRefereeClass().newInstance();
    }

    @Provides
    AbstractPlayer providePlayer() throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        return getPlayerClass().newInstance();
    }

    @Provides
    @Singleton
    GameManager<AbstractPlayer> provideGameManager(Provider<AbstractPlayer> playerProvider, Provider<AbstractReferee> refereeProvider, Gson gson) throws ClassNotFoundException {
        return new GameManager<>(playerProvider, refereeProvider, gson);
    }
}