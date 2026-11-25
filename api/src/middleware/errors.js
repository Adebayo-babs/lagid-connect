export class HttpError extends Error {
  constructor(status, code, message, details) {
    super(message);
    this.status = status;
    this.code = code;
    this.details = details;
  }
}

export function notFound(req, res) {
  res.status(404).json({
    error: {
      code: 'not_found',
      message: `No route for ${req.method} ${req.path}`,
    },
  });
}

// eslint-disable-next-line no-unused-vars
export function errorHandler(err, req, res, next) {
  const status = err.status || 500;
  if (status >= 500) console.error(err);
  res.status(status).json({
    error: {
      code: err.code || 'server_error',
      message:
        status >= 500 ? 'Something went wrong' : err.message,
      ...(err.details ? { details: err.details } : {}),
    },
  });
}

// Wrap async route handlers so errors reach errorHandler
export const wrap = (fn) => (req, res, next) =>
  Promise.resolve(fn(req, res, next)).catch(next);
