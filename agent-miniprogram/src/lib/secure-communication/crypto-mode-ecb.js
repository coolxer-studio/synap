var CRPT = {};

!(function (t, n) {
  t.CryptoJS = n();
})(CRPT, function () {
  var t =
    t ||
    (function (t, n) {
      var i =
        Object.create ||
        (function () {
          function t() {
          }

          return function (n) {
            var i;
            return (t.prototype = n), (i = new t()), (t.prototype = null), i;
          };
        })(),
        e = {},
        r = (e.lib = {}),
        o = (r.Base = (function () {
          return {
            extend: function (t) {
              var n = i(this);
              return (
                t && n.mixIn(t),
                (n.hasOwnProperty("init") && this.init !== n.init) ||
                (n.init = function () {
                  n.$super.init.apply(this, arguments);
                }),
                  (n.init.prototype = n),
                  (n.$super = this),
                  n
              );
            },
            create: function () {
              var t = this.extend();
              return t.init.apply(t, arguments), t;
            },
            init: function () {
            },
            mixIn: function (t) {
              for (var n in t) t.hasOwnProperty(n) && (this[n] = t[n]);
              t.hasOwnProperty("toString") && (this.toString = t.toString);
            },
            clone: function () {
              return this.init.prototype.extend(this);
            }
          };
        })()),
        s = (r.WordArray = o.extend({
          init: function (t, i) {
            (t = this.words = t || []),
              i != n ? (this.sigBytes = i) : (this.sigBytes = 4 * t.length);
          },
          toString: function (t) {
            return (t || c).stringify(this);
          },
          concat: function (t) {
            var n = this.words,
              i = t.words,
              e = this.sigBytes,
              r = t.sigBytes;
            if ((this.clamp(), e % 4))
              for (var o = 0; o < r; o++) {
                var s = (i[o >>> 2] >>> (24 - (o % 4) * 8)) & 255;
                n[(e + o) >>> 2] |= s << (24 - ((e + o) % 4) * 8);
              }
            else for (var o = 0; o < r; o += 4) n[(e + o) >>> 2] = i[o >>> 2];
            return (this.sigBytes += r), this;
          },
          clamp: function () {
            var n = this.words,
              i = this.sigBytes;
            (n[i >>> 2] &= 4294967295 << (32 - (i % 4) * 8)),
              (n.length = t.ceil(i / 4));
          },
          clone: function () {
            var t = o.clone.call(this);
            return (t.words = this.words.slice(0)), t;
          },
          random: function (n) {
            for (
              var i,
                e = [],
                r = function (n) {
                  var n = n,
                    i = 987654321,
                    e = 4294967295;
                  return function () {
                    (i = (36969 * (65535 & i) + (i >> 16)) & e),
                      (n = (18e3 * (65535 & n) + (n >> 16)) & e);
                    var r = ((i << 16) + n) & e;
                    return (
                      (r /= 4294967296),
                        (r += 0.5),
                      r * (t.random() > 0.5 ? 1 : -1)
                    );
                  };
                },
                o = 0;
              o < n;
              o += 4
            ) {
              var a = r(4294967296 * (i || t.random()));
              (i = 987654071 * a()), e.push((4294967296 * a()) | 0);
            }
            return new s.init(e, n);
          }
        })),
        a = (e.enc = {}),
        c = (a.Hex = {
          stringify: function (t) {
            for (var n = t.words, i = t.sigBytes, e = [], r = 0; r < i; r++) {
              var o = (n[r >>> 2] >>> (24 - (r % 4) * 8)) & 255;
              e.push((o >>> 4).toString(16)), e.push((15 & o).toString(16));
            }
            return e.join("");
          },
          parse: function (t) {
            for (var n = t.length, i = [], e = 0; e < n; e += 2)
              i[e >>> 3] |= parseInt(t.substr(e, 2), 16) << (24 - (e % 8) * 4);
            return new s.init(i, n / 2);
          }
        }),
        u = (a.Latin1 = {
          stringify: function (t) {
            for (var n = t.words, i = t.sigBytes, e = [], r = 0; r < i; r++) {
              var o = (n[r >>> 2] >>> (24 - (r % 4) * 8)) & 255;
              e.push(String.fromCharCode(o));
            }
            return e.join("");
          },
          parse: function (t) {
            for (var n = t.length, i = [], e = 0; e < n; e++)
              i[e >>> 2] |= (255 & t.charCodeAt(e)) << (24 - (e % 4) * 8);
            return new s.init(i, n);
          }
        }),
        f = (a.Utf8 = {
          stringify: function (t) {
            try {
              return decodeURIComponent(escape(u.stringify(t)));
            } catch (t) {
              throw new Error("Malformed UTF-8 data");
            }
          },
          parse: function (t) {
            return u.parse(unescape(encodeURIComponent(t)));
          }
        }),
        h = (r.BufferedBlockAlgorithm = o.extend({
          reset: function () {
            (this._data = new s.init()), (this._nDataBytes = 0);
          },
          _append: function (t) {
            "string" == typeof t && (t = f.parse(t)),
              this._data.concat(t),
              (this._nDataBytes += t.sigBytes);
          },
          _process: function (n) {
            var i = this._data,
              e = i.words,
              r = i.sigBytes,
              o = this.blockSize,
              a = 4 * o,
              c = r / a;
            c = n ? t.ceil(c) : t.max((0 | c) - this._minBufferSize, 0);
            var u = c * o,
              f = t.min(4 * u, r);
            if (u) {
              for (var h = 0; h < u; h += o) this._doProcessBlock(e, h);
              var p = e.splice(0, u);
              i.sigBytes -= f;
            }
            return new s.init(p, f);
          },
          clone: function () {
            var t = o.clone.call(this);
            return (t._data = this._data.clone()), t;
          },
          _minBufferSize: 0
        })),
        p = ((r.Hasher = h.extend({
          cfg: o.extend(),
          init: function (t) {
            (this.cfg = this.cfg.extend(t)), this.reset();
          },
          reset: function () {
            h.reset.call(this), this._doReset();
          },
          update: function (t) {
            return this._append(t), this._process(), this;
          },
          finalize: function (t) {
            t && this._append(t);
            var n = this._doFinalize();
            return n;
          },
          blockSize: 16,
          _createHelper: function (t) {
            return function (n, i) {
              return new t.init(i).finalize(n);
            };
          },
          _createHmacHelper: function (t) {
            return function (n, i) {
              return new p.HMAC.init(t, i).finalize(n);
            };
          }
        })),
          (e.algo = {}));
      return e;
    })(Math);
  return t;
});

!(function (e, t, r) {
  t(e.CryptoJS);
})(CRPT, function (e) {
  e.lib.Cipher ||
  (function (t) {
    var r = e,
      i = r.lib,
      n = i.Base,
      c = i.WordArray,
      o = i.BufferedBlockAlgorithm,
      s = r.enc,
      a = (s.Utf8, s.Base64),
      f = r.algo,
      p = f.EvpKDF,
      d = (i.Cipher = o.extend({
        cfg: n.extend(),
        createEncryptor: function (e, t) {
          return this.create(this._ENC_XFORM_MODE, e, t);
        },
        createDecryptor: function (e, t) {
          return this.create(this._DEC_XFORM_MODE, e, t);
        },
        init: function (e, t, r) {
          (this.cfg = this.cfg.extend(r)),
            (this._xformMode = e),
            (this._key = t),
            this.reset();
        },
        reset: function () {
          o.reset.call(this), this._doReset();
        },
        process: function (e) {
          return this._append(e), this._process();
        },
        finalize: function (e) {
          e && this._append(e);
          var t = this._doFinalize();
          return t;
        },
        keySize: 4,
        ivSize: 4,
        _ENC_XFORM_MODE: 1,
        _DEC_XFORM_MODE: 2,
        _createHelper: (function () {
          function e(e) {
            return "string" == typeof e ? B : x;
          }

          return function (t) {
            return {
              encrypt: function (r, i, n) {
                return e(i).encrypt(t, r, i, n);
              },
              decrypt: function (r, i, n) {
                return e(i).decrypt(t, r, i, n);
              }
            };
          };
        })()
      })),
      h = ((i.StreamCipher = d.extend({
        _doFinalize: function () {
          var e = this._process(!0);
          return e;
        },
        blockSize: 1
      })),
        (r.mode = {})),
      u = (i.BlockCipherMode = n.extend({
        createEncryptor: function (e, t) {
          return this.Encryptor.create(e, t);
        },
        createDecryptor: function (e, t) {
          return this.Decryptor.create(e, t);
        },
        init: function (e, t) {
          (this._cipher = e), (this._iv = t);
        }
      })),
      l = (h.CBC = (function () {
        function e(e, r, i) {
          var n = this._iv;
          if (n) {
            var c = n;
            this._iv = t;
          } else var c = this._prevBlock;
          for (var o = 0; o < i; o++) e[r + o] ^= c[o];
        }

        var r = u.extend();
        return (
          (r.Encryptor = r.extend({
            processBlock: function (t, r) {
              var i = this._cipher,
                n = i.blockSize;
              e.call(this, t, r, n),
                i.encryptBlock(t, r),
                (this._prevBlock = t.slice(r, r + n));
            }
          })),
            (r.Decryptor = r.extend({
              processBlock: function (t, r) {
                var i = this._cipher,
                  n = i.blockSize,
                  c = t.slice(r, r + n);
                i.decryptBlock(t, r),
                  e.call(this, t, r, n),
                  (this._prevBlock = c);
              }
            })),
            r
        );
      })()),
      _ = (r.pad = {}),
      v = (_.Pkcs7 = {
        pad: function (e, t) {
          for (
            var r = 4 * t,
              i = r - (e.sigBytes % r),
              n = (i << 24) | (i << 16) | (i << 8) | i,
              o = [],
              s = 0;
            s < i;
            s += 4
          )
            o.push(n);
          var a = c.create(o, i);
          e.concat(a);
        },
        unpad: function (e) {
          var t = 255 & e.words[(e.sigBytes - 1) >>> 2];
          e.sigBytes -= t;
        }
      }),
      y = ((i.BlockCipher = d.extend({
        cfg: d.cfg.extend({mode: l, padding: v}),
        reset: function () {
          d.reset.call(this);
          var e = this.cfg,
            t = e.iv,
            r = e.mode;
          if (this._xformMode == this._ENC_XFORM_MODE)
            var i = r.createEncryptor;
          else {
            var i = r.createDecryptor;
            this._minBufferSize = 1;
          }
          this._mode && this._mode.__creator == i
            ? this._mode.init(this, t && t.words)
            : ((this._mode = i.call(r, this, t && t.words)),
              (this._mode.__creator = i));
        },
        _doProcessBlock: function (e, t) {
          this._mode.processBlock(e, t);
        },
        _doFinalize: function () {
          var e = this.cfg.padding;
          if (this._xformMode == this._ENC_XFORM_MODE) {
            e.pad(this._data, this.blockSize);
            var t = this._process(!0);
          } else {
            var t = this._process(!0);
            e.unpad(t);
          }
          return t;
        },
        blockSize: 4
      })),
        (i.CipherParams = n.extend({
          init: function (e) {
            this.mixIn(e);
          },
          toString: function (e) {
            return (e || this.formatter).stringify(this);
          }
        }))),
      m = (r.format = {}),
      k = (m.OpenSSL = {
        stringify: function (e) {
          var t = e.ciphertext,
            r = e.salt;
          if (r)
            var i = c
              .create([1398893684, 1701076831])
              .concat(r)
              .concat(t);
          else var i = t;
          return i.toString(a);
        },
        parse: function (e) {
          var t = a.parse(e),
            r = t.words;
          if (1398893684 == r[0] && 1701076831 == r[1]) {
            var i = c.create(r.slice(2, 4));
            r.splice(0, 4), (t.sigBytes -= 16);
          }
          return y.create({ciphertext: t, salt: i});
        }
      }),
      x = (i.SerializableCipher = n.extend({
        cfg: n.extend({format: k}),
        encrypt: function (e, t, r, i) {
          i = this.cfg.extend(i);
          var n = e.createEncryptor(r, i),
            c = n.finalize(t),
            o = n.cfg;
          return y.create({
            ciphertext: c,
            key: r,
            iv: o.iv,
            algorithm: e,
            mode: o.mode,
            padding: o.padding,
            blockSize: e.blockSize,
            formatter: i.format
          });
        },
        decrypt: function (e, t, r, i) {
          (i = this.cfg.extend(i)), (t = this._parse(t, i.format));
          var n = e.createDecryptor(r, i).finalize(t.ciphertext);
          return n;
        },
        _parse: function (e, t) {
          return "string" == typeof e ? t.parse(e, this) : e;
        }
      })),
      g = (r.kdf = {}),
      S = (g.OpenSSL = {
        execute: function (e, t, r, i) {
          i || (i = c.random(8));
          var n = p.create({keySize: t + r}).compute(e, i),
            o = c.create(n.words.slice(t), 4 * r);
          return (n.sigBytes = 4 * t), y.create({key: n, iv: o, salt: i});
        }
      }),
      B = (i.PasswordBasedCipher = x.extend({
        cfg: x.cfg.extend({kdf: S}),
        encrypt: function (e, t, r, i) {
          i = this.cfg.extend(i);
          var n = i.kdf.execute(r, e.keySize, e.ivSize);
          i.iv = n.iv;
          var c = x.encrypt.call(this, e, t, n.key, i);
          return c.mixIn(n), c;
        },
        decrypt: function (e, t, r, i) {
          (i = this.cfg.extend(i)), (t = this._parse(t, i.format));
          var n = i.kdf.execute(r, e.keySize, e.ivSize, t.salt);
          i.iv = n.iv;
          var c = x.decrypt.call(this, e, t, n.key, i);
          return c;
        }
      }));
  })();
});

!(function (e, o, r) {
  o(e.CryptoJS);
})(CRPT, function (e) {
  return (
    (e.mode.ECB = (function () {
      var o = e.lib.BlockCipherMode.extend();
      return (
        (o.Encryptor = o.extend({
          processBlock: function (e, o) {
            this._cipher.encryptBlock(e, o);
          }
        })),
          (o.Decryptor = o.extend({
            processBlock: function (e, o) {
              this._cipher.decryptBlock(e, o);
            }
          })),
          o
      );
    })()),
      e.mode.ECB
  );
});

export default CRPT.CryptoJS;
