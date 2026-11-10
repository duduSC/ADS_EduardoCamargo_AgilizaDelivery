package br.com.agilizadelivery.service;

/**
 * Cliente pelo qual o usuario se autentica (UC07 E4): o ENTREGADOR usa
 * apenas o aplicativo; os demais perfis, apenas o backoffice Web.
 */
public enum CanalAcesso {
    BACKOFFICE_WEB,
    APLICATIVO
}
