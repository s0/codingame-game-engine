package com.codingame.gameengine.core;

import javax.inject.Provider;
import javax.inject.Singleton;

import org.codejargon.feather.Provides;

import com.google.gson.Gson;

    @Override
    protected void configure() {
    }

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
    AbstractReferee provideAbstractReferee(Injector injector) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        AbstractReferee referee = getRefereeClass().newInstance();
        injector.injectMembers(referee);
        return referee;
    }

    @Provides
    AbstractPlayer providePlayer(Injector injector) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        AbstractPlayer abstractPlayer = getPlayerClass().newInstance();
        injector.injectMembers(abstractPlayer);

        return abstractPlayer;
    }

    @SuppressWarnings("unchecked")
    @Provides
    @Singleton
    GameManager<AbstractPlayer> provideGameManager(Injector injector) throws ClassNotFoundException {
        Type type = Types.newParameterizedType(GameManager.class, getPlayerClass());
        GameManager<AbstractPlayer> gameManager = (GameManager<AbstractPlayer>) injector.getInstance(Key.get(type));

        return gameManager;
    }
}