const axios = require("axios");
const {
  generateKeyPair,
  exportJWK,
  calculateJwkThumbprint,
  SignJWT,
  decodeJwt,
  compactDecrypt,
  importJWK,
} = require("jose");
const {
  ESIGNET_SERVICE_URL,
  CLIENT_PRIVATE_KEY,
  USERINFO_RESPONSE_TYPE,
  JWE_USERINFO_PRIVATE_KEY,
} = require("./config");
const rateLimit = require("express-rate-limit");

const { dpopCache } = require("./cacheClient");

const alg = "RS256";
const expirationTime = "1h";
const jweEncryAlgo = "RSA-OAEP-256";
const getOidcConfigurationEndpoint = "/.well-known/openid-configuration";
const baseUrl = ESIGNET_SERVICE_URL.trim();

const get_dpopKeyAlgo = async () => {
  const endpoint = getBaseUrl(baseUrl) + getOidcConfigurationEndpoint;
  const response = await axios.get(endpoint);
  return response?.data?.dpop_signing_alg_values_supported;
};

const getBaseUrl = (serviceUrl) => {
  const url = new URL(serviceUrl.trim());
  return `${url.protocol}//${url.host}`;
};

/**
 * Generates client assertion signedJWT
 * @param {string} clientId registered client id
 * @returns client assertion signedJWT
 */
const generateSignedJwt = async (clientId, audience) => {
  // Set headers for JWT
  var header = {
    alg: alg,
    typ: "JWT",
  };

  var payload = {
    iss: clientId,
    sub: clientId,
    aud: audience,
  };

  var decodeKey = Buffer.from(CLIENT_PRIVATE_KEY, "base64")?.toString();
  const jwkObject = JSON.parse(decodeKey);
  const privateKey = await importJWK(jwkObject, alg);

  const jwt = new SignJWT(payload)
    .setProtectedHeader(header)
    .setIssuedAt()
    .setJti(Math.random().toString(36).substring(2, 7))
    .setExpirationTime(expirationTime)
    .sign(privateKey);

  return jwt;
};

const generateRandomString = (strLength = 16) => {
  let result = "";
  const characters = "abcdefghijklmnopqrstuvwxyz0123456789";

  for (let i = 0; i < strLength; i++) {
    const randomInd = Math.floor(Math.random() * characters.length);
    result += characters.charAt(randomInd);
  }
  return result;
};

const rateLimiter = rateLimit({
  windowMs: 1 * 60 * 1000,
  max: 10,
  standardHeaders: true,
  legacyHeaders: false,
  message: { message: "Too many requests, please try again later." },
});

/**
 * decrypts and decodes the user information fetched from esignet services
 * @param {string} userInfoResponse JWE encrypted or JWT encoded user information
 * @returns decrypted/decoded json user information
 */
const decodeUserInfoResponse = async (userInfoResponse) => {
  try {
    const parts = userInfoResponse.split(".");
    const isJWE =
      USERINFO_RESPONSE_TYPE.toLowerCase() === "jwe" && parts.length === 5;

    if (isJWE) {
      const jwkJson = Buffer.from(JWE_USERINFO_PRIVATE_KEY, "base64").toString(
        "utf-8"
      );
      const jwkParsed = JSON.parse(jwkJson);

      const jwk = Array.isArray(jwkParsed?.keys)
        ? jwkParsed.keys[0]
        : jwkParsed;

      if (!jwk || !jwk.kty || !jwk.d) {
        throw new Error("Invalid or missing private JWK");
      }

      jwk.alg = jwk.alg || jweEncryAlgo;

      const privateKey = await importJWK(jwk, jwk.alg);
      const { plaintext } = await compactDecrypt(userInfoResponse, privateKey);
      const decrypted = new TextDecoder().decode(plaintext);
      const decoded = decodeJwt(decrypted);
      return decoded;
    } else {
      const decoded = decodeJwt(userInfoResponse);
      return decoded;
    }
  } catch (error) {
    console.error("Failed to decode userInfoResponse:", error.message);
    throw error;
  }
};

/**
 * Generates a new key pair for a DPoP proof.
 * It's a best practice to use a new, ephemeral key for each DPoP proof.
 * @returns {Object} The generated key pair.
 */
const generateDpopKeyPair = async () => {
  let dpopKeyAlgo;
  try {
    const algos = await get_dpopKeyAlgo();
    dpopKeyAlgo = Array.isArray(algos) && algos.length > 0 ? algos[0] : "RS256";
  } catch (error) {
    dpopKeyAlgo = "RS256";
  }
  const { publicKey, privateKey } = await generateKeyPair(dpopKeyAlgo, {
    extractable: true,
  });
  const jwkPublic = await exportJWK(publicKey);
  const jwkPrivate = await exportJWK(privateKey);

  return { publicKey: jwkPublic, privateKey: jwkPrivate };
};

/**
 * Generate a public private key pair and store
 * in-memory cache and then return the dpop jkt
 * @param {string} clientId client id for the flow
 * @param {string} state state of the current flow
 * @returns {Object} a thumbprint of the dpop as dpop_jkt
 */
const generateDpopJKT = async (clientId, state) => {
  const { publicKey, privateKey } = await generateDpopKeyPair();

  dpopCache.set(
    `${clientId}###${state}`,
    JSON.stringify({ publicKey, privateKey })
  );

  const dpopJKT = await calculateJwkThumbprint({
    e: publicKey.e,
    kty: publicKey.kty,
    n: publicKey.n,
  });

  return dpopJKT;
};

/**
 * Retrieve the dpop key pair previously generated and cache in in-memory,
 * if it is not present then generate a new one and return that
 * @param {string} clientId client id for the flow
 * @param {string} state state of the current flow
 * @returns {Object} public and private key pair
 */
const retrieveDpopKeyPair = async (clientId, state) => {
  const cachedJWT = dpopCache.get(`${clientId}###${state}`);
  if (cachedJWT) {
    return JSON.parse(cachedJWT);
  }
  const dpopKeyPair = await generateDpopKeyPair();

  dpopCache.set(`${clientId}###${state}`, JSON.stringify(dpopKeyPair));
  return dpopKeyPair;
};

/**
 * Generate a dpop token, which can be send in header as dpop_header
 * @param {Object} publicKey public key jwt
 * @param {Object} privateKey private key jwt
 * @param {Object} reqPayload it may contain jti, htu, htm, iat or nonce
 * @returns return dpop_header token & public key
 */
const generateDpopJwt = async (publicKey, privateKey, reqPayload) => {
  // Create the JWT header.
  const header = {
    typ: "dpop+jwt",
    alg: alg, // The algorithm used to sign the token. Must match the key type.
    jwk: publicKey, // The JSON Web Key (public key) used for signing this token.
  };

  // Create the JWT payload.
  const payload = {
    jti: reqPayload.jti || crypto.randomUUID(), // Unique JWT ID to prevent replay attacks.
    htm: reqPayload.htm || "POST", // The HTTP method of the request.
    htu: reqPayload.htu, // The HTTP URI of the request.
    iat: reqPayload.iat || Math.floor(Date.now() / 1000), // Issued At timestamp.
  };

  if (reqPayload.nonce) {
    payload.nonce = reqPayload.nonce;
  }

  if (reqPayload.ath) {
    payload.ath = reqPayload.ath;
  }

  const actualPrivateKey = await importJWK(privateKey, alg);

  const signedJwt = await new SignJWT(payload)
    .setProtectedHeader(header)
    .setExpirationTime(expirationTime)
    .sign(actualPrivateKey);

  return { dpopToken: signedJwt, publicKey };
};

module.exports = {
  generateSignedJwt,
  generateRandomString,
  decodeUserInfoResponse,
  generateDpopKeyPair,
  retrieveDpopKeyPair,
  generateDpopJKT,
  generateDpopJwt,
  rateLimiter,
};
