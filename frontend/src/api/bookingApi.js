import request from './httpClient'

export function getReservedSeats({ movieId, theater, showDate, showtime }) {
  const params = new URLSearchParams({ movieId, theater, showDate, showtime })
  return request(`/api/bookings/reserved-seats?${params.toString()}`)
}

export function createBooking(userId, { movieId, movieTitle, theater, showDate, showtime, seats }) {
  return request('/api/bookings', {
    method: 'POST',
    userId,
    body: { movieId, movieTitle, theater, showDate, showtime, seats },
  })
}
