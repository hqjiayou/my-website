export default {
  contents: {
    title: 'Page Contents',
    description: 'Manage page blocks, copy, and publish state per site.',
  },
  reservations: {
    title: 'Reservations',
    description: 'Track reservations and handling status across multiple sites.',
    columns: {
      customer: 'Customer',
      site: 'Site',
      service: 'Service Type',
      reservedAt: 'Reserved At',
      status: 'Status',
    },
    status: {
      new: 'New',
      confirmed: 'Confirmed',
      done: 'Done',
      cancelled: 'Cancelled',
    },
  },
  files: {
    title: 'File Assets',
    description: 'Manage media assets, upload state, and usage centrally.',
  },
} as const
